package com.ken.domain;

import lombok.*;

import java.io.Serializable;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class School implements Serializable {
  private String name;
  private String address;
  private int area;

  @Singular("studentAdd")
  private List<Student> students;
}
