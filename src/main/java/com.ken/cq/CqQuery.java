package com.ken.cq;

import com.ken.domain.Student;
import com.ken.util.CacheUtils;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.query.*;

import java.util.concurrent.ThreadLocalRandom;

public class CqQuery {

  public static void main(String[] args) throws CqException, CqExistsException, RegionNotFoundException {

    ClientCache clientCache = CacheUtils.plainClient();

    CqAttributesFactory cqf = new CqAttributesFactory();
    cqf.addCqListener(new CqEventListener());
    CqAttributes cqa = cqf.create();
    String query = "select * from /Student s where s.age >= 15";
    CacheUtils.addCq(cqa, "studentTracker", query, "clientPool");

    Region<Integer, Student> region = clientCache.getRegion("Student");
    populateData(region);

//    Student stu = Student.builder().id(1).name("name1").age(17).build();
//    region.put(1, stu);

    clientCache.close(false);
  }

  private static void populateData(Region<Integer, Student> region) {
    int i = 0;
    while (i < 10) {
      int age = ThreadLocalRandom.current().nextInt(10, 20);
      Student stu = Student.builder().id(i).name("name" + i).age(age).build();
      region.put(i, stu);
      i++;
    }
  }
}
