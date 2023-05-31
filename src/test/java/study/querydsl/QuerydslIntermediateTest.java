package study.querydsl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.dto.MemberDto;
import study.querydsl.dto.QMemberDto;
import study.querydsl.dto.UserDto;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.Team;

import javax.persistence.EntityManager;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static study.querydsl.entity.QMember.*;

@SpringBootTest
@Transactional
public class QuerydslIntermediateTest {

    @Autowired
    EntityManager em;

    JPAQueryFactory queryFactory;

    @BeforeEach
    public void before(){
        queryFactory = new JPAQueryFactory(em);
        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");
        em.persist(teamA);
        em.persist(teamB);

        Member member1 = new Member("member1", 10, teamA);
        Member member2 = new Member("member2", 20, teamA);

        Member member3 = new Member("member3", 30, teamB);
        Member member4 = new Member("member4", 40, teamB);

        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);
    }

    @Test
    public void simpleProjection(){
        //프로젝션 대상이 하나면 타입을 명확하게 지정할 수 있음
        List<String> result = queryFactory
                .select(member.username)
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    public void tupleProjection(){
        //프로젝션 대상이 둘 이상이면 튜플이나 DTO로 조회
        List<Tuple> result = queryFactory
                .select(member.username, member.age)
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            String username = tuple.get(member.username);
            Integer age = tuple.get(member.age);
            System.out.println("username = " + username);
            System.out.println("age = " + age);

        }
    }

    @Test
    public void findDtoByJPQL(){
        //순수 JPA에서 DTO를 조회할 때는 new 명령어를 사용해야함
        //생성자 방식만 지원함
        List<MemberDto> result = em.createQuery("select new study.querydsl.dto.MemberDto(m.username, m.age) from Member m", MemberDto.class)
                .getResultList();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /**
     * Querydsl 빈 생성
     * 결과를 DTO 반환할 때 사용
     * 다음 3가지 방법 지원
     * 프로퍼티 접근
     * 필드 직접 접근
     * 생성자 사용
     */
    @Test
    public void findDtoBySetter(){
        //프로퍼트 사용 - Setter
        List<MemberDto> result = queryFactory
                .select(Projections.bean(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    @Test
    public void findDtoByField(){
        //필드 직접 접근
        //Getter Setter 필요없이 필드에 바로 꽂힘
        List<MemberDto> result = queryFactory
                .select(Projections.fields(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    @Test
    public void findDtoByConstructor(){
        //생성자 사용
        List<MemberDto> result = queryFactory
                .select(Projections.constructor(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    @Test
    public void findUserDto(){
        //별칭이 다를때
        QMember memberSub = new QMember("memberSub");

        List<UserDto> result = queryFactory
                .select(Projections.fields(UserDto.class,
                        //ExpressionUtils.as(member.username, "name"), 1. ExpressionUtils.as 사용
                        member.username.as("name"),   //username.as("") : 2. 필드에 별칭 적용
                        ExpressionUtils.as(JPAExpressions
                            .select(memberSub.age.max())
                                .from(memberSub),"age")
                ))
                .from(member)
                .fetch();

        for (UserDto userDto : result) {
            System.out.println("userDto = " + userDto);
        }
    }

    @Test
    public void findDtoByQueryProjection(){
        //constructor와 비슷하지만 컴파일 오류로 잡을 수 있다.
        //단점 : DTO에 QueryDSL 어노테이션을 유지해야 하는 점과 DTO까지 Q 파일을 생성해야 하는 단점이 있다.
        List<MemberDto> result = queryFactory
                .select(new QMemberDto(member.username, member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /**
     * 동적 쿼리를 해결하는 두가지 방식
     * BooleanBuilder
     * Where 다중 파라미터 사용
     */
    @Test
    public void dynamicQuery_BooleanBuilder(){
        String usernameParam = "member1";
        //Integer ageParam = 10;
        Integer ageParam = null;

        List<Member> result = searchMember1(usernameParam, ageParam);
        assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember1(String usernameCond, Integer ageCond) {

        BooleanBuilder builder = new BooleanBuilder();
        if(usernameCond != null){
            builder.and(member.username.eq(usernameCond));
        }

        if(ageCond != null){
            builder.and(member.age.eq(ageCond));
        }

        return queryFactory
                .selectFrom(member)
                .where(builder)
                .fetch();
    }

    @Test
    public void dynamicQuery_WhereParam(){
        String usernameParam = "member1";
        Integer ageParam = 10;

        List<Member> result = searchMember2(usernameParam, ageParam);
        assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember2(String usernameCond, Integer ageCond) {
        return queryFactory
                .selectFrom(member)
                .where(usernameEq(usernameCond), ageEq(ageCond))
                //.where(allEq(usernameCond, ageCond)) 조립 가능, null 처리는 주의해야함
                .fetch();
    }

    private BooleanExpression usernameEq(String usernameCond) {
        /*
        if(usernameCond == null){
            return null;
        }
        return member.username.eq(usernameCond);
        */

        return usernameCond != null ? member.username.eq(usernameCond) : null;  //삼항 연산자

    }

    private BooleanExpression ageEq(Integer ageCond) {
        return ageCond != null ? member.age.eq(ageCond) : null;
    }

    private BooleanExpression allEq(String usernameCond, Integer ageCond) {
        return usernameEq(usernameCond).and(ageEq(ageCond));
    }


    @Test
    //@Commit
    public void bulkUpdate(){

        //실행 전
        //member1 = 10  -> DB member1
        //member2 = 20  -> DB member2
        //member3 = 30  -> DB member3
        //member4 = 40  -> DB member4

        long count = queryFactory
                .update(member)
                .set(member.username, "비회원")
                .where(member.age.lt(28))
                .execute();

        //실행 후
        //member1 = 10  -> DB 비회원     영속성컨텍스트  member1
        //member2 = 20  -> DB 비회원     영속성컨텍스트  member2
        //member3 = 30  -> DB member3   영속성컨텍스트  member3
        //member4 = 40  -> DB member4   영속성컨텍스트  member4

        //DB와 영속성 컨텍스트 값이 달라진다.
        //따라서, 벌크연산 후에 em.flush, em.clear 해주자

        em.flush();
        em.clear();

        List<Member> result = queryFactory
                .selectFrom(member)
                .fetch();

        for (Member member1 : result) {
            System.out.println("member1 = " + member1);
        }
    }

    @Test
    public void bulkAdd(){
        //모든 회원들의 나이를 1씩 더해라
        long count = queryFactory
                .update(member)
                .set(member.age, member.age.add(1))
                //.set(member.age, member.age.add(-1)) 빼기
                //.set(member.age, member.age.multiply(2)) 곱하기
                .execute();
    }

    @Test
    public void bulkDelete(){
        //18살 이상인 사람 삭제
        long count = queryFactory
                .delete(member)
                .where(member.age.gt(18))
                .execute();
    }

    /**
     * SQL Function 호출하기
     * SQL function은 JPA와 같이 Dialect에 등록된 내용만 호출할 수 있다.
     * H2Dialect.java 파일보면 replace함수 등록되어있음
     */
    @Test
    public void sqlFunction(){
        //member M으로 변경하는 replace 함수 사용
        List<String> result = queryFactory
                .select(
                        Expressions.stringTemplate("function('replace', {0}, {1}, {2})", member.username, "member", "M"))
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    public void sqlFunction2(){
        //소문자로 변경해서 비교해라.
        List<String> result = queryFactory
                .select(member.username)
                .from(member)
                //.where(member.username.eq(Expressions.stringTemplate("function('lower', {0})", member.username)))
                .where(member.username.eq(member.username.lower()))  //이렇게도 가능, 표준 함수들은 Querydsl이 상당부분 내장하고 있음
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }
}
