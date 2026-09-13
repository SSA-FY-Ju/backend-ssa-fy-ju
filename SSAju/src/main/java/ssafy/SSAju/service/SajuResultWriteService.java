package ssafy.SSAju.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.SSAju.career.entity.CareerFortune;
import ssafy.SSAju.career.entity.SajuFullData;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.repository.SajuResultRepository;

/**
 * SajuResult 신규 저장 시 root와 자식 엔티티를 단일 트랜잭션으로 보호.
 *
 * SajuResult는 userProfile 기준으로 불변(immutable)이며, 여러 사용자가 공유하는 정본입니다.
 * 동일 생년월일·시각으로 요청된 결과는 기존 row를 재사용하며,
 * 삭제 후 재생성하는 패턴은 사용하지 않습니다.
 *
 * ⚠️ 로그 작성 규칙:
 * - userId만 사용 (birthDate, birthTime 등 개인정보 절대 금지)
 */
@Service
@RequiredArgsConstructor
public class SajuResultWriteService {

    private final SajuResultRepository sajuResultRepository;

    /**
     * 새 SajuResult(root)와 자식 엔티티를 함께 저장한다.
     *
     * <p>{@link ssafy.SSAju.career.provider.SajuResultProvider}가 userProfile 단위
     * 분산락 안에서만 이 메서드를 호출하므로(US5, T033) 동시 생성 경합을 별도로
     * 처리할 필요가 없다 — 단순히 root를 만들고 자식을 붙여 저장한다.
     *
     * <p>root의 userProfile은 {@code source}가 아니라 호출자가 조회·검증에 이미 사용한
     * {@code userProfile}로 만든다 — source는 자식 엔티티(사주 계산 결과)만
     * 제공하는 값으로 취급하고, 락 키·조회에 쓰인 식별자와 저장되는 식별자가 항상 같은
     * 값이도록 보장한다.
     */
    @Transactional
    public SajuResult saveNewResult(UserProfile userProfile, SajuResult source) {
        SajuResult saved = SajuResult.builder()
                .userProfile(userProfile)
                .build();

        SajuFullData srcFullData = source.getSajuFullData();
        if (srcFullData != null) {
            saved.assignSajuFullData(SajuFullData.builder()
                    .sajuResult(saved)
                    .yearPillar(srcFullData.getYearPillar())
                    .monthPillar(srcFullData.getMonthPillar())
                    .dayPillar(srcFullData.getDayPillar())
                    .hourPillar(srcFullData.getHourPillar())
                    .dayMaster(srcFullData.getDayMaster())
                    .dayMasterElement(srcFullData.getDayMasterElement())
                    .fiveElements(srcFullData.getFiveElements())
                    .solarCorrection(srcFullData.getSolarCorrection())
                    .build());
        }

        saved.assignTenGodHiddenStemAnalysis(source.getTenGodHiddenStemAnalysis());

        CareerFortune cf = source.getCareerFortune();
        if (cf != null) {
            saved.assignCareerFortune(CareerFortune.builder()
                    .sajuResult(saved)
                    .favoredPeriod(cf.getFavoredPeriod())
                    .confidenceScore(cf.getConfidenceScore())
                    .reasoning(cf.getReasoning())
                    .build());
        }

        return sajuResultRepository.save(saved);
    }
}
