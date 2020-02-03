package com.ken;

import com.ken.domain.Country;
import com.ken.domain.Student;
import com.ken.domain.Team;
import com.ken.util.CacheUtils;
import org.apache.geode.cache.Region;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unchecked")
public class TestConnection {
  private static Logger logger = LoggerFactory.getLogger(TestConnection.class);

  public static void main(String[] args) {
    System.setProperty("log4j.configurationFile", "config/log4j2.xml");

    Country china = Country.builder()
            .name("china").capitol("beijing").currency("RMB").population(140).build();
    logger.info(china.toString());

    // init
    CacheUtils.plainClient();

    // test team
    Region<Integer, Team> team = CacheUtils.getRegion("Team");
    Region<Long, Student> stu = CacheUtils.getRegion("Student");
    putTeam(team, stu);


    // test country
//    Region<String, Country> country = CacheUtils.getRegion("Country");
//    country.put(china.getName(), china);


    // test lucene index
//    SelectResults selectResults =
//            CacheUtils.executeQuery("select e.firstName, e.lastName from /EmployeeData e", CacheUtils.CLIENT_POOL);
//    logger.info("--- query result -----------");
//    selectResults.asList().forEach(it -> logger.info(it.toString()));
  }

  // select * from /Team t where t.members.containsKey('ken')
  // select * from /Team t, /Student s where t.members.containsKey(s.name)
  // -> A query on a Partitioned Region ( Team ) may not reference any other region if query is NOT executed within a Function
  private static void putTeam(Region<Integer, Team> teamRegion, Region<Long, Student> stuRegion) {
    Student ken = Student.builder().age(12).name("ken").id(123).build();
    stuRegion.put(ken.getId(), ken);

    Team team = Team.builder().id(1)
            .name(ken.getName() + ": team")
            .memberAdd(ken.getName(), ken).build();
    teamRegion.put(team.getId(), team);
  }
}
