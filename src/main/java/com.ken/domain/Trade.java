package com.ken.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Trade {
  private String name;
  private String details;
  private int price;
  private boolean isSell;
  private LocalDate localDate;
}
