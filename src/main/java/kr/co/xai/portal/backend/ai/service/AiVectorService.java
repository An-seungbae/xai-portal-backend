package kr.co.xai.portal.backend.ai.service;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.pinecone.clients.Pinecone;
import io.pinecone.clients.Index;
import io.pinecone.unsigned_indices_model.VectorWithUnsignedIndices;
import io.pinecone.unsigned_indices_model.QueryResponseWithUnsignedIndices;
import io.pinecone.unsigned_indices_model.ScoredVectorWithUnsignedIndices;
import kr.co.xai.portal.backend.ai.openai.OpenAiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiVectorService {

    private final OpenAiClient openAiClient;

    // import 충돌 방지를 위해 Full Package Name 사용
    @org.springframework.beans.factory.annotation.Value("${pinecone.api-key}")
    private String pineconeApiKey;

    @org.springframework.beans.factory.annotation.Value("${pinecone.index-name}")
    private String indexName;

    private Index index;

    @PostConstruct
    public void init() {
        try {
            // Pinecone Client 초기화
            Pinecone client = new Pinecone.Builder(pineconeApiKey).build();
            this.index = client.getIndexConnection(indexName);
            log.info("🌲 Pinecone Vector DB Connected: {}", indexName);
        } catch (Exception e) {
            log.error("Failed to connect Pinecone. RAG features will be disabled.", e);
        }
    }

    /**
     * 문서를 벡터화하여 저장 (Upsert)
     */
    public void upsertDocument(String docId, String content, Map<String, String> metadata) {
        if (index == null)
            return;

        try {
            // 1. 임베딩 생성
            List<Float> embedding = openAiClient.getEmbedding(content);

            // 2. 메타데이터 변환 (Java Map -> Protobuf Struct)
            Struct.Builder structBuilder = Struct.newBuilder();
            structBuilder.putFields("content", Value.newBuilder().setStringValue(content).build());
            metadata.forEach((k, v) -> structBuilder.putFields(k, Value.newBuilder().setStringValue(v).build()));

            // 3. Pinecone 저장 (VectorWithUnsignedIndices 사용)
            VectorWithUnsignedIndices vector = new VectorWithUnsignedIndices();
            vector.setId(docId);
            vector.setValues(embedding);
            vector.setMetadata(structBuilder.build());

            // v3.0.0 upsert 시그니처: upsert(List<VectorWithUnsignedIndices>, String namespace)
            index.upsert(Collections.singletonList(vector), null);
            log.info("✅ Vector Upserted: ID={}", docId);

        } catch (Exception e) {
            log.error("Vector Upsert Failed", e);
        }
    }

    /**
     * 질문과 유사한 문서 검색 (Search)
     */
    public List<String> searchSimilarDocuments(String query, int topK) {
        if (index == null)
            return Collections.emptyList();

        try {
            // 1. 질문 벡터화
            List<Float> queryVector = openAiClient.getEmbedding(query);

            // 2. Pinecone 검색
            QueryResponseWithUnsignedIndices response = index.query(
                    topK, // topK
                    queryVector, // vector
                    null, // sparseIndices
                    null, // sparseValues
                    null, // id
                    null, // namespace
                    null, // filter
                    true, // includeValues
                    true // includeMetadata
            );

            // 3. 결과에서 텍스트(Content) 추출
            List<String> results = new ArrayList<>();
            // [수정] getMatches() -> getMatchesList()
            if (response != null && response.getMatchesList() != null) {
                for (ScoredVectorWithUnsignedIndices match : response.getMatchesList()) {
                    // [수정] POJO 접근 방식: getMetadata()가 null이 아닌지 확인
                    if (match.getMetadata() != null) {
                        Map<String, Value> fields = match.getMetadata().getFieldsMap();
                        if (fields.containsKey("content")) {
                            results.add(fields.get("content").getStringValue());
                        }
                    }
                }
            }
            return results;

        } catch (Exception e) {
            log.error("Vector Search Failed", e);
            return Collections.emptyList();
        }
    }
}