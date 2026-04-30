package ssafy.SSAju.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.dto.external.FastAPIResponse;
import ssafy.SSAju.repository.SajuResultRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SajuResult의 DB 쓰기 작업을 별도 Service로 분리.
 *
 * 목적: delete + insert의 원자성 보장.
 * - Spring AOP 프록시를 통해 @Transactional이 제대로 작동
 * - delete 커밋 후 insert가 실패해도 transaction이 rollback되지는 않음
 *   (각각 별도 TX이므로)
 * - 하지만 delete + insert 사이 동시 insert 충돌은 catch로 처리
 * - insert 외 다른 오류 발생 시에도 명확한 로그로 추적 가능
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SajuResultWriteService {

    private final SajuResultRepository sajuResultRepository;

    /**
     * UserProfile에 대한 SajuResult를 delete + insert로 갱신.
     *
     * 단일 @Transactional 메서드로 delete, insert 구간을 보호.
     * (self-call 아님 → Spring 프록시 경유 → @Transactional 유효)
     *
     * @param userProfile UserProfile 엔티티 (id 포함)
     * @param sajuData FastAPI로부터 받은 원본 데이터
     * @param tenGodDistribution 십신 분포
     * @param hiddenStems 지장간 분포
     * @param favoredPeriod H1 또는 H2
     * @param confidenceScore 신뢰도
     * @param reasoning 분석 근거
     */
    @Transactional
    public void replaceForUserProfile(
            UserProfile userProfile,
            FastAPIResponse sajuData,
            Map<String, Integer> tenGodDistribution,
            Map<String, List<String>> hiddenStems,
            String favoredPeriod,
            int confidenceScore,
            String reasoning) {

        // 기존 결과 삭제 (JPQL 직접 실행 → 엔티티 로드/컨버터 미경유)
        sajuResultRepository.deleteByUserProfileJpql(userProfile);

        // 새 결과 생성
        Map<String, Object> careerFortune = new HashMap<>();
        careerFortune.put("favoredPeriod", favoredPeriod);
        careerFortune.put("confidenceScore", confidenceScore);
        careerFortune.put("reasoning", reasoning);

        SajuResult newResult = SajuResult.builder()
                .userProfile(userProfile)
                .fullSajuData(convertToObjectMap(sajuData))
                .hiddenStems(hiddenStems)
                .tenGodDistribution(tenGodDistribution)
                .careerFortune(careerFortune)
                .build();

        sajuResultRepository.save(newResult);
        // DataIntegrityViolationException 은 여기서 catch하지 않음.
        // → Spring이 트랜잭션을 rollback (delete 포함)하여 데이터 불일치 방지.
        // → 호출자(CareerFortuneService)가 DIVE를 받아 "선착순 결과 유지"로 처리.
    }

    private Map<String, Object> convertToObjectMap(FastAPIResponse r) {
        Map<String, Object> map = new HashMap<>();
        map.put("heavenlyStems", r.heavenlyStems());
        map.put("earthlyBranches", r.earthlyBranches());
        map.put("fiveElements", r.fiveElements());
        map.put("yearPillar", r.yearPillar());
        map.put("monthPillar", r.monthPillar());
        map.put("dayPillar", r.dayPillar());
        map.put("hourPillar", r.hourPillar());
        map.put("birthTime", r.birthTime());
        map.put("birthDate", r.birthDate());
        return map;
    }
}
