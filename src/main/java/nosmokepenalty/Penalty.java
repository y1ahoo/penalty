package nosmokepenalty;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import java.util.List;

@Entity
@Table(name="Penalty_table")
public class Penalty {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private Long point;

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


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public Long getPoint() {
        return point;
    }

    public void setPoint(Long point) {
        this.point = point;
    }




}
