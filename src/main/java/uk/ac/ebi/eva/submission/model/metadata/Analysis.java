package uk.ac.ebi.eva.submission.model.metadata;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Analysis {

    @JsonProperty("analysisTitle")
    private String title;

    @JsonProperty("analysisAlias")
    private String alias;

    private String description;

    private ExperimentType experimentType;

    private String referenceGenome;

    // TODO other properties

}
