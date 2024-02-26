package eu.ubitech.onenet.dto;

import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterDto {

    @NotNull
    @NotBlank
    private String username;

    @NotNull
    @NotBlank
    private String email;

    @NotEmpty
    private List<String> realmRoles = new ArrayList<>();

    @NotNull
    @NotBlank
    private String password;

    @NotNull
    @NotBlank
    private String confirmPassword;
}
