![image](https://user-images.githubusercontent.com/70302884/96567670-0504e000-1302-11eb-877c-375e23c5f20f.png)

# 흡연구역 패널티 서비스

본 서비스는 흡연구역에서 흡연한 경우, Point 가 적립되며 적립된 Point 을 사용하는 기능에 패널티 서비스를 추가한 서비스입니다.
CNA 개발에 요구되는 체크포인트를 만족하기 위하여 분석/설계/구현/운영 전단계를 포함하도록 구성하였습니다. 


# Table of contents

- [흡연구역 패널티 서비스](#---)
  - [서비스 시나리오](#서비스-시나리오)
  - [분석/설계](#분석설계)
  - [구현:](#구현-)
    - [DDD 의 적용](#ddd-의-적용)
    - [Saga](#Saga)
    - [CQRS](#CQRS)
    - [동기식 호출 과 Fallback 처리](#동기식-호출-과-Fallback-처리)
    - [비동기식 호출 과 Eventual Consistency](#비동기식-호출-과-Eventual-Consistency)
    - [폴리글랏 퍼시스턴스/폴리글랏 프로그래밍](#폴리글랏 퍼시스턴스/폴리글랏 프로그래밍)
  - [운영](#운영)
    - [오토스케일 아웃](#오토스케일-아웃)
    - [Istio 적용](#Istio 적용)
    - [Kiali](#Kiali)
    - [Jaeger](#Jaeger)

# 서비스 시나리오

기능적 요구사항
1. 고객이 흡연구역에서 체크인 한다.
1. 고객이 흡연구역에서 체크아웃 한다.
1. 체크아웃 되면 포인트를 적립한다.
1. 고객이 포인트를 사용한다.
1. 포인트가 사용되면 적립된 포인트에서 차감한다.
1. 포인트가 차감되면 포인트 내역을 업데이트 한다.
1. 고객이 포인트 내역을 확인한다.
+ 고객이 비흡연구역 흡연으로 패널티를 적립한다.
+ 고객이 패널티가 누적되면 흡연이 금지된다.
+ 패널티가 차감되어 흡연이 가능하다.
+ 패널티가 차감되면 패널티 내역을 업데이트 한다.
+ 고객이 패널티 내역을 확인한다.

비기능적 요구사항
1. 트랜잭션
    1. 포인트 차감이 이루어지지 않은 포인트 결제는 성립되지 않는다. Sync 호출
    + 패널티는 포인트에 꼭 적립되어야한다. Sync 호출
2. 장애격리
    1. 포인트 결제, 포인트 적립 기능이 수행되지 않더라도 흡연장소 체크인/아웃은 작동되어야 한다. Async (event-driven), Eventual Consistency
    + 패널티는 꼭 적립되어야하고, 흡연 가능 여부는 수행되지 않아도 된다. Async (event-driven), eventual Consistency
3. 성능
    1. 고객이 흡연장소에 체크인/아웃한 내역과 포인트 적립 내역을 mypage에서 확인이 가능해야 한다. CQRS
    1. 고객이 포인트 결제하여 차감된 내역을 mypage에서 확인이 가능해야 한다. CQRS
    1. 체크아웃후 포인트 적립이 완료되면 적립상태가 체크인 내역에 업데이트 되어야 한다. SAGA
    + 고객이 패널티를 받은 내역은 mypage에서 확인이 가능하다. CQRS
    + 고객의 패널티가 차감된 내역을 mypage에서 확인이 가능하다. CQRS
    + 패널티 적립이 완료되면 패널티 적립 상태가 업데이트 되어야한다. SAGA


# 분석/설계


## AS-IS 조직 (Horizontally-Aligned)
  ![image](https://user-images.githubusercontent.com/487999/79684144-2a893200-826a-11ea-9a01-79927d3a0107.png)

## TO-BE 조직 (Vertically-Aligned)
  ![image](https://user-images.githubusercontent.com/70302884/96571555-ad1ca800-1306-11eb-8951-0621a1469146.png)


## Event Storming 결과
* MSAEz 로 모델링한 이벤트스토밍 결과:  http://www.msaez.io/#/storming/jKYCuD4xjigZX1zkJZUpEXQvHCW2/mine/51c226dfdf0ea6d33f60d58d35c7b640/-MK8xBV9cf_cWb56QObr


### 이벤트 도출
![image](https://user-images.githubusercontent.com/70302884/96573041-911a0600-1308-11eb-8506-1efc69f8a481.png)

### 부적격 이벤트 탈락
![image](https://user-images.githubusercontent.com/70302884/96574180-1651ea80-130a-11eb-9337-f8e08478713e.png)

    - 과정중 도출된 잘못된 도메인 이벤트들을 걸러내는 작업을 수행함
        - 장소 체크인시>흡연 구역변경/등록됨, 포인트 적립/차감시>등록됨, 사용자 정보>사용자 정보 등록/변경/삭제 :  별도 이벤트이지, 메인 업무적인 의미의 이벤트가 아니라서 제외

### 액터, 커맨드 부착하여 읽기 좋게
![image](https://user-images.githubusercontent.com/70302884/96574093-f9b5b280-1309-11eb-9a8b-34eb81f718ec.png)

### 어그리게잇으로 묶기
![image](https://user-images.githubusercontent.com/70302884/96574602-a1cb7b80-130a-11eb-8bf7-c15dee072f02.png)

    - pay의 지불, point 의 차감은 그와 연결된 command 와 event 들에 의하여 트랜잭션이 유지되어야 하는 단위로 그들 끼리 묶어줌

### 바운디드 컨텍스트로 묶기

![image](https://user-images.githubusercontent.com/70302884/96575104-4bab0800-130b-11eb-9d9c-dde7958dd0db.png)

    - 도메인 서열 분리
        - Core Domain:  checkIn : 없어서는 안될 핵심 서비스이며, 연견 Up-time SLA 수준을 99.999% 목표, 배포주기는 app 의 경우 1주일 1회 미만, store 의 경우 1개월 1회 미만
        - Supporting Domain:   point, pay : 경쟁력을 내기위한 서비스이며, SLA 수준은 연간 60% 이상 uptime 목표, 배포주기는 각 팀의 자율이나 표준 스프린트 주기가 1주일 이므로 1주일 1회 이상을 기준으로 함.
        - General Domain:   지도서비스 : Google Maps 등 3rd Party 외부 서비스를 사용하는 것이 경쟁력이 높음 (핑크색으로 이후 전환할 예정)

### 폴리시 부착 (괄호는 수행주체, 폴리시 부착을 둘째단계에서 해놔도 상관 없음. 전체 연계가 초기에 드러남)

![image](https://user-images.githubusercontent.com/70302884/96575590-e73c7880-130b-11eb-87d5-ab72fdaaa809.png)

### 폴리시의 이동과 컨텍스트 매핑 (점선은 Pub/Sub, 실선은 Req/Resp)

![image](https://user-images.githubusercontent.com/70302884/96575916-5ade8580-130c-11eb-8b5c-62c791ef3c67.png)

### 완성된 1차 모형

![image](https://user-images.githubusercontent.com/70302884/96576148-b4df4b00-130c-11eb-859b-b7bd7a8648cd.png)

    - View Model 추가

### 1차 완성본에 대한 기능적/비기능적 요구사항을 커버하는지 검증

![image](https://user-images.githubusercontent.com/70302884/96577104-1d7af780-130e-11eb-8f92-7b8aa5b9a1da.png)

    - 고객이 흡연구역에서 체크인 한다. (ok)
    - 고객이 흡연구역에서 체크아웃 한다. (ok)
    - 체크아웃 되면 포인트를 적립한다. (ok)

![image](https://user-images.githubusercontent.com/70302884/96577425-937f5e80-130e-11eb-955f-a3da4136dca7.png)
    
    - 고객이 포인트를 사용한다. (ok)
    - 포인트가 사용되면 포인트에서 차감한다. (ok) 


### 모델 확인

![image](https://user-images.githubusercontent.com/70302884/96577613-d2adaf80-130e-11eb-913b-6562d629f177.png)
    
    - 수정된 모델은 모든 요구사항을 커버함.

### 비기능 요구사항에 대한 검증

![image](https://user-images.githubusercontent.com/16397080/96833154-e8d87e80-147a-11eb-8090-7a9073ffdf41.png)

    - 마이크로 서비스를 넘나드는 시나리오에 대한 트랜잭션 처리
    - 포인트 결제 처리: 포인트 차감이 완료되지 않으면 결제가 이루어지지 않아야 함.(ACID 트랜잭션 적용) 포인트 결제는 Request-Response 방식 처리
    - 체크아웃 및 포인트 적립 처리:  checkIn 에서 point 마이크로서비스로 포인트 적립 요청이 전달되는 과정에 있어서 point 마이크로 서비스가 별도의 배포주기를 가지기 때문에 Eventual Consistency 방식으로 트랜잭션 처리함.
    - 나머지 모든 inter-microservice 트랜잭션: 포인트 적립 상태 등 모든 이벤트에 대해 데이터 일관성의 시점이 크리티컬하지 않은 모든 경우가 대부분이라 판단, Eventual Consistency 를 기본으로 채택함.


## 헥사고날 아키텍처 다이어그램 도출
    
![image](https://user-images.githubusercontent.com/16397080/96833164-ec6c0580-147a-11eb-9b13-01e8312871af.png)


    - Chris Richardson, MSA Patterns 참고하여 Inbound adaptor와 Outbound adaptor를 구분함
    - 호출관계에서 PubSub 과 Req/Resp 를 구분함
    - 서브 도메인과 바운디드 컨텍스트의 분리:  각 팀의 KPI 별로 아래와 같이 관심 구현 스토리를 나눠가짐


# 구현:

분석/설계 단계에서 도출된 헥사고날 아키텍처에 따라, 각 BC별로 대변되는 마이크로 서비스들을 스프링부트와 JPA으로 구현하였다. 구현한 각 서비스를 로컬에서 실행하는 방법은 아래와 같다 (각자의 포트넘버는 8081 ~ 808n 이다)

```
cd checkIn
mvn spring-boot:run

cd point
mvn spring-boot:run 

cd pay
mvn spring-boot:run  

cd customercenter
python policy-handler.py 
```

## DDD 의 적용

- 각 서비스내에 도출된 핵심 Aggregate Root 객체를 Entity 로 선언하였다: (예시는 point 마이크로 서비스). 이때 가능한 현업에서 사용하는 언어 (유비쿼터스 랭귀지)를 그대로 사용하려고 노력했다. 하지만, 일부 구현에 있어서 영문이 아닌 경우는 실행이 불가능한 경우가 있기 때문에 계속 사용할 방법은 아닌것 같다. (Maven pom.xml, Kafka의 topic id, FeignClient 의 서비스 id 등은 한글로 식별자를 사용하는 경우 오류가 발생하는 것을 확인하였다)

```
package nosmoke;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import java.util.List;

@Entity
@Table(name="Earn_table")
public class Earn {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private Long checkInId;
    private Long point;
    private String status;

    @PostPersist
    public void onPostPersist(){
        Earned earned = new Earned();
        BeanUtils.copyProperties(this, earned);
        earned.publishAfterCommit();


    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public Long getCheckInId() {
        return checkInId;
    }

    public void setCheckInId(Long checkInId) {
        this.checkInId = checkInId;
    }
    public Long getPoint() {
        return point;
    }

    public void setPoint(Long point) {
        this.point = point;
    }


    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

```
- Entity Pattern 과 Repository Pattern 을 적용하여 JPA 를 통하여 다양한 데이터소스 유형 (RDB or NoSQL) 에 대한 별도의 처리가 없도록 데이터 접근 어댑터를 자동 생성하기 위하여 Spring Data REST 의 RestRepository 를 적용하였다
```
package nosmoke;

import org.springframework.data.repository.PagingAndSortingRepository;

public interface EarnRepository extends PagingAndSortingRepository<Earn, Long>{

}
```
- 적용 후 REST API 의 테스트
```
# checkIn 서비스의 체크인 처리
http http://localhost:8081/checkIns smokingAreaId="453!#FEQ"

# checkIn 서비스의 체크아웃 후 point 서비스의 적립 처리
http PUT http://localhost:8081/checkIns/1 point=100

# 적립 상태 확인
http http://localhost:8081/checkIns/1
http http://localhost:8082/earns/1

```
## 폴리글랏 퍼시스턴스/폴리글랏 프로그래밍

프로젝트 진행 시 customercenter는 H2가 아닌  hsql db를 적용하였다.
hsql db는 application.yml 파일에는 설정하지 않았으며 customercenter의 dependencies에만 추가하여 진행하였다.

[!image](https://user-images.githubusercontent.com/16397080/96833162-ebd36f00-147a-11eb-86a8-aef353ff2566.png)

## Saga

penalty 서비스에서 패널티를 받은 후 point 서비스에서 패널티 적립을 Eventual Consistency 방식으로 처리했기 때문에 point 서비스에서 패널티 적립 처리가 완료되면 penalty 서비스의 포인트를 업데이트 시켜주는 SAGA 패턴을 적용하였다. 이 기능 역시 비동기 방식으로 penalty의 PolicyHandler에 처리되도록 구현하였다.

```
package nosmokepenalty;

@Service
public class PolicyHandler{
    @StreamListener(KafkaProcessor.INPUT)
    public void onStringEventListener(@Payload String eventString){

    }

    @Autowired
    PenaltyRepository PenaltyRepository;
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverProhibited_UpdatePenalty(@Payload Prohibited prohibited) {

        if (prohibited.isMe()) {
            Optional<Penalty> penaltyInOptional = PenaltyRepository.findById(prohibited.getPenaltyId());
            Penalty penalty = penaltyInOptional.get();
            penalty.setPoint(prohibited.getPoint());
            penalty.setId(prohibited.getPenaltyId());
            PenaltyRepository.save(penalty);
        }


    }
}

```

## CQRS

고객관리 서비스(customercenter)의 시나리오인 체크인/포인트적립, 패널티 적립과 패널티 차감, 포인트결제에 따른 포인트차감, 패널티 내역을 CQRS로 구현하었고 코드는 다음과 같다:
```
package nosmokepenalty;

@Service
public class MypageViewHandler {


    @Autowired
    private MypageRepository mypageRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void whenCheckIned_then_CREATE_1 (@Payload CheckIned checkIned) {
        try {
            if (checkIned.isMe()) {
                // view 객체 생성
                Mypage mypage = new Mypage();
                // view 객체에 이벤트의 Value 를 set 함
                mypage.setUserId(checkIned.getId());
                mypage.setSmokingAreaId(checkIned.getAreaId());
                // view 레파지 토리에 save
                mypageRepository.save(mypage);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void whenPaid_then_CREATE_2 (@Payload Paid paid) {
        try {
            if (paid.isMe()) {
                // view 객체 생성
                Mypage mypage = new Mypage();
                // view 객체에 이벤트의 Value 를 set 함
                mypage.setDeductId(paid.getId());
                mypage.setPoint(paid.getPoint());
                // view 레파지 토리에 save
                mypageRepository.save(mypage);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void whenPenaltied_then_CREATE_3 (@Payload Penaltied penaltied) {
        try {
            if (penaltied.isMe()) {
                // view 객체 생성
                Mypage mypage = new Mypage();
                // view 객체에 이벤트의 Value 를 set 함
                mypage.setPenaltyId(penaltied.getId());
                mypage.setPoint(penaltied.getPoint());
                // view 레파지 토리에 save
                mypageRepository.save(mypage);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }


    @StreamListener(KafkaProcessor.INPUT)
    public void whenEarned_then_UPDATE_1(@Payload Earned earned) {
        try {
            if (earned.isMe()) {
                // view 객체 조회
                List<Mypage> mypageList = mypageRepository.findByUserId(earned.getUserId());
                for(Mypage mypage : mypageList){
                    // view 객체에 이벤트의 eventDirectValue 를 set 함
                    mypage.setEarnId(earned.getId());
                    mypage.setPoint(earned.getPoint());
                    // view 레파지 토리에 save
                    mypageRepository.save(mypage);
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void whenPenaltied_then_UPDATE_2(@Payload Penaltied penaltied) {
        try {
            if (penaltied.isMe()) {
                // view 객체 조회
                List<Mypage> mypageList = mypageRepository.findByPenaltyId(penaltied.getId());
                for(Mypage mypage : mypageList){
                    // view 객체에 이벤트의 eventDirectValue 를 set 함
                    mypage.setPenaltyId(penaltied.getId());
                    mypage.setPenaltyId(penaltied.getPoint());
                    // view 레파지 토리에 save
                    mypageRepository.save(mypage);
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

}
```

## 동기식 호출 과 Fallback 처리

분석단계에서의 조건 중 하나로 패널티적립(penalty)->패널티차감(point) 간의 호출은 동기식 일관성을 유지하는 트랜잭션으로 처리하기로 하였다. 호출 프로토콜은 이미 앞서 Rest Repository 에 의해 노출되어있는 REST 서비스를 FeignClient 를 이용하여 호출하도록 한다. 

- 결제서비스를 호출하기 위하여 Stub과 (FeignClient) 를 이용하여 Service 대행 인터페이스 (Proxy) 를 구현

```
@FeignClient(name="point", url="${api.point.url}")
public interface ProhibitService {

    @RequestMapping(method= RequestMethod.GET, path="/prohibits")
    public void penalty(@RequestBody Prohibit prohibit);

}
```

- 패널티를 받은 직후(@PostPersist) 결제를 요청하도록 처리

```
    @PostPersist
    public void onPostPersist(){
        Penaltied penaltied = new Penaltied();
        BeanUtils.copyProperties(this, penaltied);
        penaltied.publishAfterCommit();

        //Following code causes dependency to external APIs
        // it is NOT A GOOD PRACTICE. instead, Event-Policy mapping is recommended.

        nosmokepenalty.external.Prohibit prohibit = new nosmokepenalty.external.Prohibit();
        // mappings goes here
        PenaltyApplication.applicationContext.getBean(nosmokepenalty.external.ProhibitService.class)
            .penalty(prohibit);


    }
```

- 동기식 호출에서는 호출 시간에 따른 타임 커플링이 발생하며, point 시스템이 장애가 나면 패널티도 못받는다는 것을 확인:


```
# point 서비스를 잠시 내려놓음 (spring-boot:stop)

#포인트결제 처리
http http://localhost:8083/pays point=100    #Fail

#point 서비스 재기동
cd point
mvn spring-boot:run

#주문처리
http http://localhost:8083/pays point=100   #Success
```

- 또한 과도한 요청시에 서비스 장애가 도미노 처럼 벌어질 수 있다. (서킷브레이커, 폴백 처리는 운영단계에서 설명한다.)




## 비동기식 호출 / 시간적 디커플링 / 장애격리 / 최종 (Eventual) 일관성 테스트


패널티적립이 이루어진 후에 penalty 서비스로 이를 알려주는 행위는 동기식이 아니라 비 동기식으로 처리하여 penalty 서비스의 처리를 위하여 체크인/아웃이 블로킹 되지 않아도록 처리한다.

 
```
    @PostPersist
    public void onPostPersist(){
        Penaltied penaltied = new Penaltied();
        BeanUtils.copyProperties(this, penaltied);
        penaltied.publishAfterCommit();


    }

    @PostUpdate
    public void onPostUpdate(){
        Prohibited prohibited = new Prohibited();
        BeanUtils.copyProperties(this, prohibited);
        prohibited.publishAfterCommit();


    }

    }
```

- point 서비스에서는 penaltied 이벤트에 대해서 이를 수신하여 자신의 정책을 처리하도록 PolicyHandler 를 구현한다:

```
    @Autowired
    ProhibitRepository ProhibitRepository;
    
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverProhibited_Prohibited(@Payload Prohibited prohibited){

        if(prohibited.isMe()){
            Prohibit prohibit = new Prohibit();
            prohibit.setPenaltyId(prohibited.getId());
            prohibit.setPoint(prohibited.getPoint());

            ProhibitRepository.save(prohibit);
        }
    }

```

penalty 시스템은 포인트적립/사용과 완전히 분리되어있으며, 이벤트 수신에 따라 처리되기 때문에, point 서비스가 유지보수로 인해 잠시 내려간 상태라도 주문을 받는데 문제가 없다.

```
# point 서비스 를 잠시 내려놓음 (ctrl+c)

#체크인/아웃 처리
http localhost:8081/checkIns smokingAreaId=1   #Success
http PUT localhost:8081/checkIns point=200     #Success

#체크인 포인트 적립 상태 확인
http localhost:8081/checkIns/1     # 포인트 적립 상태 안바뀜 확인

#point 서비스 기동
cd point
mvn spring-boot:run

#체크인 포인트 적립 상태 확인
http localhost:8081/checkIns/1     # 모든 체크인 상태가 "EARNED"로 확인
```

## Gateway를 통한 진입점 통일

gateway를 통해 checkIn, point, pay, penalty, customercenter 등 모든 서비스에 진입할 수 있도록 yaml 파일에 적용

```
spring:
  profiles: docker
  cloud:
    gateway:
      routes:
        - id: checkIn
          uri: http://skccuer21-checkIn:8080
          predicates:
            - Path=/checkIns/** 
        - id: point
          uri: http://skccuer21-point:8080
          predicates:
            - Path=/deducts/**,/earns/**,/prohibits/**
        - id: pay
          uri: http://skccuer21-pay:8080
          predicates:
            - Path=/pays/** 
        - id: customercenter
          uri: http://skccuer21-customercenter:8080
          predicates:
            - Path= /mypages/**
        - id: penalty
          uri: http://skccuer21-penalty:8080
          predicates:
            - Path=/penalties/** 
      globalcors:
        corsConfigurations:
          '[/**]':
            allowedOrigins:
              - "*"
            allowedMethods:
              - "*"
            allowedHeaders:
              - "*"
            allowCredentials: true

server:
  port: 8080
  
```
gateway를 통한 진입점 통일 테스트

```
http http://skccuser21-point:8080/earns/1  #point 서비스에 직접 진입

http http://skccuser21-gateway:8080/earns/1  #point 서비스에 gateway를 통해 진입(결과값 같음)
```

# 운영

### 오토스케일 아웃

* kubectl autoscale deploy pay --min1 --max=10 --cpu-percent=15 -n tutorial로 오토스케일 설정을 완료하여 아래에서 MAXPODS 10에 도달한 것을 확인

![image](https://user-images.githubusercontent.com/16397080/96833150-e6762480-147a-11eb-8446-4deab0d8007f.png)

* checkin pod가 10개까지 오토스케일 아웃되었음

![image](https://user-images.githubusercontent.com/16397080/96833101-d52d1800-147a-11eb-8af2-36ec51e4c21e.png)

## Istio 적용

* Istio 모니터링 툴을 설치하고 istio를 enable 설정한 다음 deploy를 배포하여 각 pod들이side-car pattern으로 생성되어 있는 것을 확인

![image](https://user-images.githubusercontent.com/16397080/96833166-ed049c00-147a-11eb-829f-e74ae2109abe.png)


## Kiali

* Monitoring Server - Kiali를 적용하였다. 아래는 6시간 전부터 호출된 서비스에 대해 Graph 형식으로 보여지는 모니터링 결과를 확인

![image](https://user-images.githubusercontent.com/16397080/96833160-ebd36f00-147a-11eb-8aed-05b696ab91cc.png)


## Jaeger

* Tracing Server - Jaeger를 적용하였다. 아래는 6시간 전부터 gateway로 동기 호출된 결과에 대해 Trace 결과를 보여주고 있음을 확인 

![image](https://user-images.githubusercontent.com/16397080/96833402-5684aa80-147b-11eb-93bd-3a47e5c52b40.png)




