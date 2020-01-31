package com.ken.domain;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class Student {
  private long id;
  private String name;
  private int age;
  private String email;
}
