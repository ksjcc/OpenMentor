package com.dto.profile;

import java.util.List;

import lombok.Data;

@Data
public class ProfileUpdatePatchDTO {
  private List<ProfileFieldPatchDTO> updates;
}
