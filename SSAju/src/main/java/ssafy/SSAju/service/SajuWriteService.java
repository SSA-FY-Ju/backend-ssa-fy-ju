package ssafy.SSAju.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.SSAju.career.converter.AnalysisSnapshotCodec;
import ssafy.SSAju.career.domain.AnalysisPayload;
import ssafy.SSAju.career.entity.Manse;
import ssafy.SSAju.career.entity.Saju;
import ssafy.SSAju.career.enums.AnalysisType;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.entity.enums.UserStatus;
import ssafy.SSAju.exception.UserNotFoundException;
import ssafy.SSAju.repository.SajuRepository;
import ssafy.SSAju.repository.UserRepository;

/**
 * 성공한 분석 결과 한 건을 커밋하는 짧은 쓰기 트랜잭션 경계.
 *
 * <p>외부 호출과 계산은 호출자가 트랜잭션 밖에서 끝낸 뒤 완성된 스냅샷만 넘긴다. 이 트랜잭션이
 * 커밋되어야 비로소 성공한 분석으로 본다 — 그 전의 실패는 결과를 남기지 않고 쿼터를 원래 날짜로
 * 복원하며, 커밋 이후의 ID 주입·응답 전송 실패는 결과를 그대로 둔다.
 *
 * <p>직렬화 가능 여부를 사용자 행을 잠그기 전에 먼저 확인한다. 잠금 구간을 최대한 짧게 유지하기
 * 위해서다.
 *
 * <p>기존 결과를 갱신·병합하거나 유일 제약 경합에서 승자의 결과를 대신 돌려주는 경로는 두지
 * 않는다. 성공 요청 하나당 INSERT 하나다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SajuWriteService {

    private final SajuRepository sajuRepository;
    private final UserRepository userRepository;
    private final AnalysisSnapshotCodec snapshotCodec;

    @Transactional
    public Long save(Long userId, AnalysisType type, Manse manse,
                     AnalysisPayload.Input input, AnalysisPayload.Result result) {
        String inputJson = snapshotCodec.writeInput(type, input);
        String resultJson = snapshotCodec.writeResult(type, result);

        requireActiveUser(userId);

        Long analysisId = sajuRepository.save(Saju.builder()
                .userId(userId)
                .analysisType(type)
                .manse(manse)
                .inputJson(inputJson)
                .resultJson(resultJson)
                .build()).getId();

        log.info("분석 결과 저장 완료: userId={}, analysisType={}, analysisId={}", userId, type, analysisId);
        return analysisId;
    }

    /**
     * 사용자 행에 쓰기 잠금을 걸어 탈퇴와 순서를 정한다. 탈퇴가 먼저 커밋됐다면 이 조회가 비거나
     * 비활성 상태이므로 최종 저장을 거부한다.
     */
    private void requireActiveUser(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new UserNotFoundException(ErrorMessageConstants.USER_NOT_FOUND.getMessage()));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new UserNotFoundException(ErrorMessageConstants.USER_NOT_FOUND.getMessage());
        }
    }
}
