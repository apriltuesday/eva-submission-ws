package uk.ac.ebi.eva.submission.model.metadata;

import com.fasterxml.jackson.annotation.JsonProperty;

public class File {

    private String analysisAlias;

    private String fileName;

    @JsonProperty("md5")
    private String md5checksum;

    private Long fileSize;
}
