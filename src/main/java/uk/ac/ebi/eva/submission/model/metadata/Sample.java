package uk.ac.ebi.eva.submission.model.metadata;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Sample {

    private String analysisAlias;

    private String sampleInVcf;

    private String bioSampleAccession;

}
