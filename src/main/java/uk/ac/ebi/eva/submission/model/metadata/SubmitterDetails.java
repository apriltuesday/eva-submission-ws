package uk.ac.ebi.eva.submission.model.metadata;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SubmitterDetails {

    private String lastName;

    private String firstName;

    private String telephone;

    private String email;

    private String laboratory;

    private String centre;

    private String address;

}
