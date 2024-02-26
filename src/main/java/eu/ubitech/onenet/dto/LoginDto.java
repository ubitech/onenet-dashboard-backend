package eu.ubitech.onenet.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginDto {

  @NotNull
  @NotBlank
  private String username;

  @NotNull
  @NotBlank
  private String password;

}
