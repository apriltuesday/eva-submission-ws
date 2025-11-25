package uk.ac.ebi.eva.submission.service;

import com.elixir.mars.repository.MarsReceiptException;
import com.elixir.mars.repository.MarsReceiptProvider;
import com.elixir.mars.repository.ReceiptAccessionsMap;
import com.elixir.mars.repository.models.isa.Assay;
import com.elixir.mars.repository.models.isa.Comment;
import com.elixir.mars.repository.models.isa.DataFile;
import com.elixir.mars.repository.models.isa.IsaJson;
import com.elixir.mars.repository.models.isa.Study;
import com.elixir.mars.repository.models.receipt.MarsReceipt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import uk.ac.ebi.eva.submission.entity.Submission;
import uk.ac.ebi.eva.submission.model.metadata.Analysis;
import uk.ac.ebi.eva.submission.model.metadata.EvaMetadata;
import uk.ac.ebi.eva.submission.model.metadata.File;
import uk.ac.ebi.eva.submission.model.metadata.Project;
import uk.ac.ebi.eva.submission.model.metadata.Sample;
import uk.ac.ebi.eva.submission.model.metadata.SubmitterDetails;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MarsConversionService extends MarsReceiptProvider {

    private ReceiptAccessionsMap accessionMap;

    public MarsConversionService() {
        super("eva");
        accessionMap = new ReceiptAccessionsMap();
    }

    public JsonNode convertMarsToEvaJson(IsaJson marsIsaJson) {
        EvaMetadata evaMetadata = new EvaMetadata();

        // Assume single study
        Study study = marsIsaJson.getInvestigation().getStudies().get(0);

        // Create Submitter Details
        List<SubmitterDetails> submitterDetails = study.getPeople().stream().map(
                person -> new SubmitterDetails(
                        person.getLastName(), person.getFirstName(), person.getPhone(), person.getEmail(),
                        // TODO laboratory vs. centre?
                        person.getAffiliation(), person.getAffiliation(), person.getAddress())).collect(
                Collectors.toList());
        evaMetadata.setSubmitterDetails(submitterDetails);

        // Create Project
        // Assume taxonomy ID and centre are study comments
        Integer taxonomyId = null;
        String centre = null;
        for (Comment comment : study.getComments()) {
            if (comment.getName().equalsIgnoreCase("taxonomyId")) {
                taxonomyId = Integer.valueOf(comment.getValue());
            } else if (comment.getName().equalsIgnoreCase("centre")) {
                centre = comment.getValue();
            }
        }
        evaMetadata.setProject(new Project(study.getTitle(), study.getDescription(), taxonomyId, centre));

        // Assume ISA is already filtered to only include EVA assays, and each assay should be a different analysis
        List<Analysis> analyses = new ArrayList<>();
        for (Assay assay : study.getAssays()) {
            String analysisAlias = null;
            // TODO needs a new version of mars-repository-lib
            for (Comment comment : assay.getComments()) {
                if (comment.getName().equalsIgnoreCase("analysisAlias")) {
                    analysisAlias = comment.getValue();
                }
            }

            List<File> files = new ArrayList<>();
            List<Sample> samples = new ArrayList<>();

            // Start with data files
            for (DataFile dataFile : assay.getDataFiles()) {
                String md5checksum = null;
                Long fileSize = null;
                for (Comment comment :  dataFile.getComments()) {
                    if (comment.getName().equalsIgnoreCase("MD5")) {
                        md5checksum = comment.getValue();
                    }
                    if (comment.getName().equalsIgnoreCase("fileSize")) {
                        fileSize = Long.valueOf(comment.getValue());
                    }
                }
                files.add(new File(analysisAlias, dataFile.getName(), md5checksum, fileSize));

                // "Walk up" to get analysis parameters TODO

                // End with samples TODO
            }

            evaMetadata.setFiles(files);
            evaMetadata.setSamples(samples);
            evaMetadata.setAnalyses(analyses);
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
        throw new UnsupportedOperationException("Not supported");
    }
}
