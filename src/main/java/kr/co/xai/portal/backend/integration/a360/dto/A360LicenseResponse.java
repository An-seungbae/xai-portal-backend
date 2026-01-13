package kr.co.xai.portal.backend.integration.a360.dto;

import lombok.*;
import java.util.List;
import lombok.Data;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class A360LicenseResponse {
    private String type;
    private String installationDate;
    private String expirationDate;
    private List<Product> products;

    @Data
    public static class Product {
        private String id;
        private String name;
        private List<Feature> features;
        private List<Metric> metrics;
    }

    @Data
    public static class Feature {
        private String id;
        private String name;
        private boolean enable;
        private int purchasedCount;
        private int usedCountByThisCr;
        private int usedCountByAllCr;
    }

    @Data
    public static class Metric {
        private String name;
        private String unit;
        private int purchasedCount;
        private int usedCountByThisCr;
        private int availableCount;
    }
}