package com.ken.domain;

import lombok.*;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class School {
  private String name;
  private String address;
  private int area;

  @Singular("studentAdd")
  private List<Student> students;
}
