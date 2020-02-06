package com.ken;

import com.ken.domain.Student;
import com.ken.domain.Team;
import com.ken.util.CacheUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.geode.cache.Region;

/*
Partition Region can not join each others.

Order By 使用(orde by的字段必须在 select clause之中):
select distinct p.id, p.status from /region p where p.id > 5 order by p.status limit 10;
order by, limit will apply on each nodes and collected

 */
@Slf4j
public class PartitionRegionTest {
  public static void main(String[] args) {
    // init
    CacheUtils.plainClient();

    // test team
    Region<Integer, Team> team = CacheUtils.getRegion("Team");
    Region<Long, Student> stu = CacheUtils.getRegion("Student");
    putTeam(team, stu);
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
