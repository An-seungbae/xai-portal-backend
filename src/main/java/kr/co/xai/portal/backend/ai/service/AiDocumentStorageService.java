package kr.co.xai.portal.backend.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.xai.portal.backend.ai.dto.AiHistoryListDto;
import kr.co.xai.portal.backend.ai.dto.AiImageSaveRequest;
import kr.co.xai.portal.backend.ai.dto.AiImageSaveResponse;
import kr.co.xai.portal.backend.ai.entity.AiDocument;
import kr.co.xai.portal.backend.ai.entity.AiDocumentField;
import kr.co.xai.portal.backend.ai.entity.AiDocumentHistory;
import kr.co.xai.portal.backend.ai.repository.AiDocumentFieldRepository;
import kr.co.xai.portal.backend.ai.repository.AiDocumentHistoryRepository;
import kr.co.xai.portal.backend.ai.repository.AiDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiDocumentStorageService {

    private final AiDocumentRepository documentRepository;
    private final AiDocumentFieldRepository fieldRepository;
    private final AiDocumentHistoryRepository historyRepository;
    private final ObjectMapper objectMapper;
    private final AiVectorService vectorService; // [추가] Vector DB 서비스 주입

    // 파일 저장 경로 (프로젝트 루트/uploads)
    private final Path fileStorageLocation = Paths.get("uploads").toAbsolutePath().normalize();

    /**
     * [기존] 이미지 분석 결과 저장 (물리 파일 O)
     */
    @Transactional
    public AiImageSaveResponse save(AiImageSaveRequest request, MultipartFile file) {
        String savedFilePath = null;
        if (file != null && !file.isEmpty()) {
            try {
                Files.createDirectories(fileStorageLocation);
                String originalName = file.getOriginalFilename();
                String ext = (originalName != null && originalName.lastIndexOf(".") > 0)
                        ? originalName.substring(originalName.lastIndexOf("."))
                        : "";
                String savedFileName = UUID.randomUUID().toString() + ext;
                Path targetPath = fileStorageLocation.resolve(savedFileName);

                try (InputStream inputStream = file.getInputStream()) {
                    Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
                savedFilePath = targetPath.toString();
            } catch (IOException e) {
                throw new RuntimeException("파일 저장 중 오류가 발생했습니다.", e);
            }
        }

        // DB 저장
        AiDocument doc = new AiDocument();
        doc.setSourceFileName(request.getSourceFileName());
        doc.setFilePath(savedFilePath);

        String jsonString = "{}";
        String docType = "UNKNOWN";

        try {
            if (request.getAnalysisResult() != null) {
                jsonString = objectMapper.writeValueAsString(request.getAnalysisResult());
                if (request.getAnalysisResult().getStructuredData() != null) {
                    Object typeObj = request.getAnalysisResult().getStructuredData().get("documentType");
                    if (typeObj != null) {
                        docType = String.valueOf(typeObj);
                    }
                }
            }
        } catch (Exception e) {
            jsonString = "{}";
        }

        doc.setDocumentType(docType);
        doc.setAnalysisJson(jsonString);

        AiDocument savedDoc = documentRepository.save(doc);

        // 필드 및 이력 저장
        saveFields(savedDoc.getId(), request);
        saveHistory(savedDoc.getId(), "CREATE", jsonString);

        return new AiImageSaveResponse(savedDoc.getId(), "SUCCESS");
    }

    /**
     * [RAG 연동] AI 지식 학습 데이터 저장 (물리 파일 X, 텍스트 데이터)
     * - DB 저장 후 Vector DB에도 Upsert 합니다.
     */
    @Transactional
    public void saveDocument(String externalId, String title, String author, String content, String tags) {
        // 1. 엔티티 생성
        AiDocument doc = new AiDocument();
        doc.setSourceFileName(title); // 목록에서 식별하기 쉽도록 제목을 사용
        doc.setDocumentType(tags); // 태그 정보
        doc.setFilePath(null); // 텍스트 데이터

        // 2. 내용(Content)을 JSON 구조로 변환하여 저장
        Map<String, String> knowledgeMap = new HashMap<>();
        knowledgeMap.put("externalId", externalId);
        knowledgeMap.put("author", author);
        knowledgeMap.put("content", content); // 핵심 지식

        String jsonString = "{}";
        try {
            jsonString = objectMapper.writeValueAsString(knowledgeMap);
        } catch (Exception e) {
            log.error("JSON 변환 실패", e);
        }
        doc.setAnalysisJson(jsonString);

        // 3. DB 저장
        AiDocument savedDoc = documentRepository.save(doc);

        // 4. [추가] Vector DB에 저장 (RAG 인덱싱)
        try {
            Map<String, String> metadata = new HashMap<>();
            metadata.put("title", title);
            metadata.put("author", author);
            metadata.put("tags", tags);

            // 문서 ID를 Key로 하여 Pinecone에 벡터 저장
            vectorService.upsertDocument(savedDoc.getId().toString(), content, metadata);
            log.info("Vector Indexing Success: docId={}", savedDoc.getId());

        } catch (Exception e) {
            log.warn("Vector indexing failed for doc: {}", savedDoc.getId(), e);
            // 벡터 저장이 실패하더라도 DB 저장은 유지 (선택 사항)
        }

        // 5. 이력 남기기
        saveHistory(savedDoc.getId(), "LEARN", jsonString);
    }

    private void saveFields(Long documentId, AiImageSaveRequest request) {
        if (request.getAnalysisResult() != null && request.getAnalysisResult().getStructuredData() != null) {
            Object fieldsObj = request.getAnalysisResult().getStructuredData().get("fields");
            if (fieldsObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> fields = (Map<String, Object>) fieldsObj;
                for (Map.Entry<String, Object> entry : fields.entrySet()) {
                    String key = entry.getKey();
                    Object valObj = entry.getValue();

                    if (valObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> valMap = (Map<String, Object>) valObj;
                        AiDocumentField field = new AiDocumentField();
                        field.setDocumentId(documentId);
                        field.setFieldKey(key);
                        field.setFieldValue(String.valueOf(valMap.get("value")));
                        if (valMap.get("confidence") instanceof Number) {
                            field.setConfidence(((Number) valMap.get("confidence")).doubleValue());
                        }
                        fieldRepository.save(field);
                    }
                }
            }
        }
    }

    private void saveHistory(Long documentId, String action, String jsonSnapshot) {
        AiDocumentHistory history = new AiDocumentHistory();
        history.setDocumentId(documentId);
        history.setAction(action);
        history.setSnapshotJson(jsonSnapshot);
        historyRepository.save(history);
    }

    /**
     * 이미지 파일 로딩
     */
    public Resource loadFileAsResource(Long documentId) {
        AiDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("문서가 존재하지 않습니다. ID: " + documentId));

        if (doc.getFilePath() == null) {
            throw new IllegalArgumentException("저장된 이미지 파일 경로가 없습니다.");
        }

        try {
            Path filePath = Paths.get(doc.getFilePath());
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() || resource.isReadable()) {
                return resource;
            } else {
                throw new IllegalArgumentException("파일을 찾을 수 없거나 읽을 수 없습니다: " + doc.getFilePath());
            }
        } catch (Exception ex) {
            throw new RuntimeException("파일을 불러오는데 실패했습니다.", ex);
        }
    }

    @Transactional(readOnly = true)
    public Page<AiHistoryListDto> getAllHistory(Pageable pageable) {
        return documentRepository.findAll(pageable)
                .map(doc -> AiHistoryListDto.builder()
                        .id(doc.getId())
                        .documentType(doc.getDocumentType())
                        .sourceFileName(doc.getSourceFileName())
                        .createdAt(doc.getCreatedAt())
                        .build());
    }

    @Transactional(readOnly = true)
    public AiDocument getDocumentDetail(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 문서 ID입니다: " + id));
    }
}