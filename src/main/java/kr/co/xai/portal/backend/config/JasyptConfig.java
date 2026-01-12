package kr.co.xai.portal.backend.config;

/* 
import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
*/
// Jasypt 암호화 설정 클래스
// @Configuration
public class JasyptConfig {
    /*
     * // 실행 시 외부에서 주입받을 암호화 키
     * 
     * @Value("${jasypt.encryptor.password}")
     * private String encryptKey;
     * 
     * @Bean("jasyptStringEncryptor")
     * public StringEncryptor stringEncryptor() {
     * PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
     * SimpleStringPBEConfig config = new SimpleStringPBEConfig();
     * 
     * // 암호화 키 설정
     * config.setPassword(encryptKey);
     * // 암호화 알고리즘 (PBEWithMD5AndDES 등 사용 가능, 강력한 보안을 위해 알고리즘 선택 신중)
     * config.setAlgorithm("PBEWithMD5AndDES");
     * config.setKeyObtentionIterations("1000");
     * config.setPoolSize("1");
     * config.setProviderName("SunJCE");
     * config.setSaltGeneratorClassName("org.jasypt.salt.RandomSaltGenerator");
     * config.setIvGeneratorClassName("org.jasypt.iv.NoIvGenerator");
     * config.setStringOutputType("base64");
     * 
     * encryptor.setConfig(config);
     * return encryptor;
     * }
     */
}