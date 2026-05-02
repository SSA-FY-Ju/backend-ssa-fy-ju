package ssafy.SSAju.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.repository.CareerFortuneRepository;
import ssafy.SSAju.repository.HiddenStemDataRepository;
import ssafy.SSAju.repository.SajuResultRepository;
import ssafy.SSAju.repository.TenGodDataRepository;

/**
 * SajuResult의 replace(삭제+저장) 작업을 단일 트랜잭션으로 보호.
 * JPA cascade delete 대신 JPQL 직접 삭제를 사용하여 동시 요청 시 락 충돌 방지.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SajuResultWriteService {

    private final SajuResultRepository sajuResultRepository;
    private final TenGodDataRepository tenGodDataRepository;
    private final HiddenStemDataRepository hiddenStemDataRepository;
    private final CareerFortuneRepository careerFortuneRepository;

    @Transactional
    public void replaceForUserProfile(UserProfile userProfile, SajuResult newResult) {
        sajuResultRepository.findByUserProfile(userProfile).ifPresent(existing -> {
            Long existingId = existing.getId();
            tenGodDataRepository.deleteBySajuResultId(existingId);
            hiddenStemDataRepository.deleteBySajuResultId(existingId);
            careerFortuneRepository.deleteBySajuResultId(existingId);
            sajuResultRepository.deleteByUserProfileJpql(userProfile);
        });
        sajuResultRepository.save(newResult);
    }
}
