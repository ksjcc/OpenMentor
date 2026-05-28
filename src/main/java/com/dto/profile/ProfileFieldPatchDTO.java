package com.dto.profile;

import lombok.Data;

@Data
public class ProfileFieldPatchDTO {
  private String field;

  private String op;

  private String name;

  private String value;

  private Integer delta;

  private Double confidence;

  private String evidence;
}
