package com.ken.domain;

import lombok.*;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class Student implements Serializable {
  private long id;
  private String name;
  private int age;
  private String email;
}
