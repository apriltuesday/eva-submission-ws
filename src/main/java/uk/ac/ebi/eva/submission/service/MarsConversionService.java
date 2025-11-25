package uk.ac.ebi.eva.submission.service;

import com.elixir.mars.repository.MarsReceiptException;
import com.elixir.mars.repository.MarsReceiptProvider;
import com.elixir.mars.repository.ReceiptAccessionsMap;
import com.elixir.mars.repository.models.isa.Assay;
import com.elixir.mars.repository.models.isa.Characteristic;
import com.elixir.mars.repository.models.isa.CharacteristicCategory;
import com.elixir.mars.repository.models.isa.CharacteristicType;
import com.elixir.mars.repository.models.isa.Comment;
import com.elixir.mars.repository.models.isa.DataFile;
import com.elixir.mars.repository.models.isa.ExecutesProtocol;
import com.elixir.mars.repository.models.isa.Input;
import com.elixir.mars.repository.models.isa.IsaJson;
import com.elixir.mars.repository.models.isa.Output;
import com.elixir.mars.repository.models.isa.Parameter;
import com.elixir.mars.repository.models.isa.ParameterName;
import com.elixir.mars.repository.models.isa.ParameterValue;
import com.elixir.mars.repository.models.isa.ProcessSequence;
import com.elixir.mars.repository.models.isa.Protocol;
import com.elixir.mars.repository.models.isa.Sample;
import com.elixir.mars.repository.models.isa.Study;
import com.elixir.mars.repository.models.receipt.MarsReceipt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import uk.ac.ebi.eva.submission.entity.Submission;
import uk.ac.ebi.eva.submission.model.metadata.Analysis;
import uk.ac.ebi.eva.submission.model.metadata.EvaMetadata;
import uk.ac.ebi.eva.submission.model.metadata.ExperimentType;
import uk.ac.ebi.eva.submission.model.metadata.File;
import uk.ac.ebi.eva.submission.model.metadata.Project;
import uk.ac.ebi.eva.submission.model.metadata.EvaSample;
import uk.ac.ebi.eva.submission.model.metadata.SubmitterDetails;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MarsConversionService extends MarsReceiptProvider {

    private ReceiptAccessionsMap accessionMap;

    // Names of Comments and Parameters - must be kept in sync with ISA-JSON producer
    private static final String TAXONOMY_ID_KEY = "taxonomyId";
    private static final String CENTRE_KEY = "centre";
    private static final String MD5_CHECKSUM_KEY = "MD5";
    private static final String FILE_SIZE_KEY = "fileSize";
    private static final String ANALYSIS_TITLE_KEY = "analysisTitle";
    private static final String ANALYSIS_ALIAS_KEY = "analysisAlias";
    private static final String ANALYSIS_DESCRIPTION_KEY = "description";
    private static final String EXPERIMENT_TYPE_KEY = "experimentType";
    private static final String REFERENCE_GENOME_KEY = "reference";
    private static final String SAMPLE_IN_VCF_KEY = "sampleInVcf";
    private static final String SAMPLE_ACCESSION_KEY = "accession";

    public MarsConversionService() {
        super("eva");
        accessionMap = new ReceiptAccessionsMap();
    }

    public JsonNode convertMarsToEvaJson(IsaJson marsIsaJson) {
        EvaMetadata evaMetadata = new EvaMetadata();

        // Assume single study
        Study study = marsIsaJson.getInvestigation().getStudies().get(0);

        // Study level maps to help find entities by ID
        Map<String, List<Parameter>> protocolParamsMap = new HashMap<>();
        for (Protocol protocol : study.getProtocols()) {
            protocolParamsMap.put(protocol.getId(), protocol.getParameters());
        }
        Map<String, Sample> sampleMap = new HashMap<>();
        for (Sample sample : study.getMaterials().getSamples()) {
            sampleMap.put(sample.getId(), sample);
        }
        Map<String, CharacteristicType> characteristicCategoryMap = new HashMap<>();
        for (CharacteristicCategory cc : study.getCharacteristicCategories()) {
            characteristicCategoryMap.put(cc.getId(), cc.getCharacteristicType());
        }

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
            if (comment.getName().equalsIgnoreCase(TAXONOMY_ID_KEY)) {
                taxonomyId = Integer.valueOf(comment.getValue());
            } else if (comment.getName().equalsIgnoreCase(CENTRE_KEY)) {
                centre = comment.getValue();
            }
        }
        evaMetadata.setProject(new Project(study.getTitle(), study.getDescription(), taxonomyId, centre));

        // Assume ISA is already filtered to only include EVA assays
        List<Analysis> analyses = new ArrayList<>();
        for (Assay assay : study.getAssays()) {

            Map<String, ProcessSequence> processMap = new HashMap<>();
            for (ProcessSequence process : assay.getProcessSequence()) {
                processMap.put(process.getId(), process);
            }

            List<File> files = new ArrayList<>();
            List<EvaSample> samples = new ArrayList<>();

            // Start with data files
            for (DataFile dataFile : assay.getDataFiles()) {
                String md5checksum = null;
                Long fileSize = null;
                for (Comment comment :  dataFile.getComments()) {
                    if (comment.getName().equalsIgnoreCase(MD5_CHECKSUM_KEY)) {
                        md5checksum = comment.getValue();
                    }
                    if (comment.getName().equalsIgnoreCase(FILE_SIZE_KEY)) {
                        fileSize = Long.valueOf(comment.getValue());
                    }
                }

                // Create Analysis from process sequence
                ProcessSequence process = findProcessByOutputId(assay.getProcessSequence(), dataFile.getId());
                ExecutesProtocol protocol = process.getExecutesProtocol();
                String analysisTitle = null;
                String analysisAlias = null;
                String analysisDescription = null;
                ExperimentType experimentType = null;
                String referenceGenome = null;

                Map<String, String> paramIdToValueMap = new HashMap<>();
                for (ParameterValue paramVal : process.getParameterValues()) {
                    paramIdToValueMap.put(paramVal.getCategory().getId(), paramVal.getValue().getAnnotationValue());
                }

                for (Parameter param : protocolParamsMap.get(protocol.getId())) {
                    ParameterName paramName = param.getParameterName();
                    if (paramName.getAnnotationValue().equalsIgnoreCase(ANALYSIS_TITLE_KEY)) {
                        analysisTitle = paramIdToValueMap.get(param.getId());
                    }
                    if (paramName.getAnnotationValue().equalsIgnoreCase(ANALYSIS_ALIAS_KEY)) {
                        analysisAlias = paramIdToValueMap.get(param.getId());
                    }
                    if (paramName.getAnnotationValue().equalsIgnoreCase(ANALYSIS_DESCRIPTION_KEY)) {
                        analysisDescription = paramIdToValueMap.get(param.getId());
                    }
                    if (paramName.getAnnotationValue().equalsIgnoreCase(EXPERIMENT_TYPE_KEY)) {
                        experimentType = ExperimentType.valueOf(paramIdToValueMap.get(param.getId()).toUpperCase());
                    }
                    if (paramName.getAnnotationValue().equalsIgnoreCase(REFERENCE_GENOME_KEY)) {
                        referenceGenome = paramIdToValueMap.get(param.getId());
                    }
                }

                files.add(new File(analysisAlias, dataFile.getName(), md5checksum, fileSize));
                analyses.add(new Analysis(analysisTitle, analysisAlias, analysisDescription, experimentType,
                                          referenceGenome));

                // End with samples
                // Walk back until you have no previous process, to be sure to collect all sample inputs
                ProcessSequence currentProcess = process;
                do {
                    for (Input input : currentProcess.getInputs()) {
                        if (sampleMap.containsKey(input.getId())) {
                            Sample sampleToAdd = sampleMap.get(input.getId());
                            String sampleInVcf = null;
                            String bioSampleAccession = null;
                            for (Characteristic characteristic : sampleToAdd.getCharacteristics()) {
                                String characteristicName = characteristicCategoryMap.get(characteristic.getCategory().getId()).getAnnotationValue();
                                if (characteristicName.equalsIgnoreCase(SAMPLE_IN_VCF_KEY)) {
                                    sampleInVcf = characteristic.getValue().getAnnotationValue();
                                }
                                if (characteristicName.equalsIgnoreCase(SAMPLE_ACCESSION_KEY)) {
                                    bioSampleAccession = characteristic.getValue().getAnnotationValue();
                                }
                            }
                            samples.add(new EvaSample(analysisAlias, sampleInVcf, bioSampleAccession));
                        }
                    }
                    currentProcess = processMap.get(currentProcess.getPreviousProcess().getId());
                } while (currentProcess != null);
            }

            evaMetadata.setFiles(files);
            evaMetadata.setSamples(samples);
            evaMetadata.setAnalyses(analyses);
        }

        // TODO store ReceiptAccessionsMap as state and use this to build the successful receipt

        ObjectMapper mapper = new ObjectMapper();
        return mapper.valueToTree(evaMetadata);
    }

    private ProcessSequence findProcessByOutputId(ArrayList<ProcessSequence> processSequence, String outputId) {
        if (processSequence == null || outputId == null) {
            return null;
        }

        for (final ProcessSequence process : processSequence) {
            if (process.getOutputs() != null) {
                for (final Output output : process.getOutputs()) {
                    if (outputId.equals(output.getId())) {
                        return process;
                    }
                }
            }
        }
        return null;
    }

    public MarsReceipt buildMarsSuccessfulReceipt(Submission submission) {
        // TODO provide submission ID in receipt
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
