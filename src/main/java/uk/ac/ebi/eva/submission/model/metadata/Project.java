package uk.ac.ebi.eva.submission.model.metadata;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Project {

    private String title;

    private String description;

    @JsonProperty("taxId")
    private Integer taxonomyId;

    private String centre;

    // TODO other properties

}
