package ssafy.SSAju.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.SSAju.career.entity.ExpectedInterviewQuestion;

import java.util.List;

public interface ExpectedInterviewQuestionRepository extends JpaRepository<ExpectedInterviewQuestion, Long> {

    List<ExpectedInterviewQuestion> findByCompanyCompatibility_Id(Long compatibilityId);
}
