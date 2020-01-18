package com.ken.index;


import com.ken.domain.Contact;
import com.ken.domain.EmployeeData;
import com.ken.util.CacheUtils;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.lucene.LuceneQuery;
import org.apache.geode.cache.lucene.LuceneQueryException;
import org.apache.geode.cache.lucene.LuceneService;
import org.apache.geode.cache.lucene.LuceneServiceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class LuceneDemo {
  private static Logger logger = LoggerFactory.getLogger(LuceneDemo.class);

  final static String EmployeeRegion = "EmployeeData";
  private final static String nameIndex = "name_index";
  final static String ANALYZER_INDEX = "analyzerIndex";
  final static String NESTEDOBJECT_INDEX = "nestedObjectIndex";

  // https://www.cnblogs.com/xing901022/p/4974977.html lucene 查询
  public static void main(String[] args) throws LuceneQueryException {
    // connect to the locator using default port 10334
    ClientCache cache = CacheUtils.plainClient();

    // create a local region that matches the server region
    Region<Integer, EmployeeData> region = CacheUtils.getRegion(EmployeeRegion);

    insertValues(region);
//    query(cache);
    queryNestedObject(cache);
    cache.close();
  }


  private static void query(ClientCache cache) throws LuceneQueryException {

    LuceneService lucene = LuceneServiceProvider.get(cache);
    LuceneQuery<Integer, EmployeeData> query = lucene.createLuceneQueryFactory()
            .create(nameIndex, EmployeeRegion, "firstName:*ie*", "firstname");

    query.findResults().forEach(System.out::println);
  }

  private static void queryNestedObject(ClientCache cache) throws LuceneQueryException {
    LuceneService lucene = LuceneServiceProvider.get(cache);
    LuceneQuery<Integer, EmployeeData> query = lucene.createLuceneQueryFactory().create(
            NESTEDOBJECT_INDEX, EmployeeRegion, "5035330001 AND 5036430001", "contacts.phoneNumbers");

    query.findValues().forEach(System.out::println);
  }

  public static void insertValues(Map<Integer, EmployeeData> region) {
    // insert values into the region
    String[] firstNames = "Alex,Bertie,Kris,Dale,Frankie,Jamie,Morgan,Pat,Ricky,Taylor".split(",");
    String[] lastNames = "Able,Bell,Call,Driver,Forth,Jive,Minnow,Puts,Reliable,Tack".split(",");
    String[] contactNames = "Jack,John,Tom,William,Nick,Jason,Daniel,Sue,Mary,Mark".split(",");
    int salaries[] = new int[]{60000, 80000, 75000, 90000, 100000};
    int hours[] = new int[]{40, 40, 40, 30, 20};
    int emplNumber = 10000;
    for (int index = 0; index < firstNames.length; index++) {
      emplNumber = emplNumber + index;

      String email = firstNames[index] + "." + lastNames[index] + "@example.com";
      // Generating random number between 0 and 100000 for salary
      int salary = salaries[index % 5];
      int hoursPerWeek = hours[index % 5];

      Contact contact1 = new Contact(contactNames[index] + " Jr",
              new String[]{"50353" + (30000 + index), "50363" + (30000 + index)});
      Contact contact2 = new Contact(contactNames[index],
              new String[]{"50354" + (30000 + index), "50364" + (30000 + index)});

      EmployeeData val = EmployeeData.builder().firstName(firstNames[index]).lastName(lastNames[index])
              .emplNumber(emplNumber)
              .email(email)
              .salary(salary)
              .hoursPerWeek(hoursPerWeek)
              .contactsAdd(contact1)
              .contactsAdd(contact2).build();

      region.put(emplNumber, val);
    }
  }
}
