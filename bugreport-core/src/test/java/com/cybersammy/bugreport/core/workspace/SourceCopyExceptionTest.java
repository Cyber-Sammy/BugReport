package com.cybersammy.bugreport.core.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cybersammy.bugreport.api.specification.LogicalRoot;
import com.cybersammy.bugreport.api.specification.RelativePath;
import com.cybersammy.bugreport.core.session.ReportSessionId;
import org.junit.jupiter.api.Test;

final class SourceCopyExceptionTest {
    @Test
    void exposesAStableCodeAndPathFreeStructuredContext() {
        SourceCopyException exception = new SourceCopyException(
                SourceCopyCode.SOURCE_UNSAFE,
                ReportSessionId.parse("00000000-0000-4000-8000-000000000101"),
                LogicalRoot.GAME_LOGS,
                RelativePath.of("private/server/latest.log"),
                "Source is unsafe");

        assertEquals("source_copy.source_unsafe", exception.errorCode().value());
        assertEquals(
                "session=00000000-0000-4000-8000-000000000101,root=GAME_LOGS",
                exception.errorContext().logToken());
    }
}
