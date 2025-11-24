package uk.ac.ebi.eva.submission.service;

import com.elixir.mars.repository.MarsReceiptException;
import com.elixir.mars.repository.MarsReceiptProvider;
import com.elixir.mars.repository.ReceiptAccessionsMap;
import com.elixir.mars.repository.models.isa.Assay;
import com.elixir.mars.repository.models.isa.IsaJson;
import com.elixir.mars.repository.models.isa.Study;
import com.elixir.mars.repository.models.receipt.MarsReceipt;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import uk.ac.ebi.eva.submission.entity.Submission;
import uk.ac.ebi.eva.submission.model.metadata.EvaMetadata;

import java.util.Collections;
import java.util.List;

@Service
public class MarsConversionService extends MarsReceiptProvider {

    private ReceiptAccessionsMap accessionMap;

    public MarsConversionService() {
        super("eva");
        accessionMap = new ReceiptAccessionsMap();
    }

    public JsonNode convertMarsToEvaJson(IsaJson marsIsaJson) {
        EvaMetadata  evaMetadata = new EvaMetadata();

        // Assume single study
        Study study = marsIsaJson.getInvestigation().getStudies().get(0);
        // TODO: submitterDetails, project from Study

        // Assume single study and that ISA is already filtered to only include EVA assays
        for (Assay assay : study.getAssays()) {
            // Assume each assay should be a different analysis
            // Start from data files
            // TODO: analysis, sample, files from Assays
        }

        // TODO store ReceiptAccessionsMap as state and use this to build the successful receipt

        ObjectMapper mapper = new ObjectMapper();
        return mapper.valueToTree(evaMetadata);
    }

    public MarsReceipt buildMarsSuccessfulReceipt(Submission submission) {
        // TODO
        return null;
    }

    public MarsReceipt buildMarsFailedReceipt(String message) {
        buildMarsReceipt(null, null, null, null, null, null, Collections.singletonList(message), null);
        return getMarsReceipt();
    }

    public MarsReceipt buildMarsFailedReceipt(MarsReceiptException exception) {
        // TODO - for more detailed errors, i.e. with paths
        return null;
    }

    @Override
    public String convertMarsReceiptToJson() {
        // Not used
        return "";
    }
}
