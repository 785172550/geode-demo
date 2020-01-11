package com.ken.domain;

import lombok.*;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Country {
  private String name;
  private String capitol;
  private String language;
  private String currency;
  private int population;

  @Singular("schoolAdd")
  private List<School> schools;
}
