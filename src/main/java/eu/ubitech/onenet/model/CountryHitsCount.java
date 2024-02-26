package eu.ubitech.onenet.model;

import lombok.Data;

@Data
public class CountryHitsCount {
    private String countryIso;
    private String countryName;
    private Long hitsCount;

    public CountryHitsCount(String iso, String name, Long hits) {
        countryIso = iso;
        countryName = name;
        hitsCount = hits;
    }
}
