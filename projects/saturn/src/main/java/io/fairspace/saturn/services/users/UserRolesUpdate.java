package io.fairspace.saturn.services.users;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class UserRolesUpdate {
    private String id;

    @JsonProperty("isAdmin")
    private Boolean admin;
}
