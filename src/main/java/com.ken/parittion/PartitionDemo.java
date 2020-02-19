package com.ken.parittion;

import com.ken.domain.Trade;
import com.ken.util.CacheUtils;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.client.ClientCache;

import java.time.LocalDate;

public class PartitionDemo {
  public static void main(String[] args) {
    // connect to the locator using default port 10334
    ClientCache cache = CacheUtils.plainClient();
    Region<String, Trade> trades = cache.getRegion("Trade");

//    trades.removeAll(trades.keySetOnServer());
//    putData(trades);
  }

  private static void putData(Region<String, Trade> trades) {
    Trade trade1 = Trade.builder().name("支付宝账单1月-水").details("水费")
            .price(50).localDate(LocalDate.now().minusDays(30)).build();

    Trade trade2 = Trade.builder().name("支付宝账单1月-电").details("电费")
            .price(120).localDate(LocalDate.now().minusDays(30)).build();

    Trade trade3 = Trade.builder().name("支付宝账单2月-水").details("水费")
            .price(50).localDate(LocalDate.now()).build();

    trades.put(trade1.getName() + ":" + trade1.getLocalDate().getMonth().getValue(), trade1);
    trades.put(trade2.getName() + ":" + trade2.getLocalDate().getMonth().getValue(), trade2);
    trades.put(trade3.getName() + ":" + trade3.getLocalDate().getMonth().getValue(), trade3);
  }
}
