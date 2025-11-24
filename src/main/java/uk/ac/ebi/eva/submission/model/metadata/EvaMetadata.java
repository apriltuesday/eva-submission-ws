package uk.ac.ebi.eva.submission.model.metadata;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class EvaMetadata {

    private List<SubmitterDetails> submitterDetails;

    private Project project;

    @JsonProperty("analysis")
    private List<Analysis> analyses;

    @JsonProperty("sample")
    private List<Sample> samples;

    private List<File> files;

}
