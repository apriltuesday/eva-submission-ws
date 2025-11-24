package uk.ac.ebi.eva.submission.model.metadata;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Project {

    private String title;

    private String description;

    @JsonProperty("taxId")
    private int taxonomyId;

    private String centre;

    // TODO other properties

}
