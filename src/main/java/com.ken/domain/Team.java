package com.ken.domain;

import lombok.*;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class Team {
  private int id;
  private String name;

  @Singular("memberAdd")
  private Map<String, Student> members;
}
