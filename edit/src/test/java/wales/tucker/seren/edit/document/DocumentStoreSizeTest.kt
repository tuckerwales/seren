package wales.tucker.seren.edit.document

import org.junit.Assert.assertEquals
import org.junit.Test

/** Size gates for opening files: soft warn at 2 MB, hard refuse at 16 MB. */
class DocumentStoreSizeTest {

    @Test
    fun underWarnOpensStraightAway() {
        assertEquals(FileOpenDecision.Open, DocumentStore.openDecision(0))
        assertEquals(FileOpenDecision.Open, DocumentStore.openDecision(null))
        assertEquals(FileOpenDecision.Open, DocumentStore.openDecision(DocumentStore.WARN_FILE_BYTES))
    }

    @Test
    fun betweenWarnAndMaxAsksFirst() {
        assertEquals(
            FileOpenDecision.Confirm,
            DocumentStore.openDecision(DocumentStore.WARN_FILE_BYTES + 1),
        )
        assertEquals(
            FileOpenDecision.Confirm,
            DocumentStore.openDecision(DocumentStore.MAX_FILE_BYTES),
        )
    }

    @Test
    fun overMaxIsRefused() {
        assertEquals(
            FileOpenDecision.Refuse,
            DocumentStore.openDecision(DocumentStore.MAX_FILE_BYTES + 1),
        )
        assertEquals(
            FileOpenDecision.Refuse,
            DocumentStore.openDecision(32L * 1024 * 1024),
        )
    }

    @Test
    fun limitsMatchTheDocumentedMegabytes() {
        assertEquals(2L * 1024 * 1024, DocumentStore.WARN_FILE_BYTES)
        assertEquals(16L * 1024 * 1024, DocumentStore.MAX_FILE_BYTES)
    }
}
