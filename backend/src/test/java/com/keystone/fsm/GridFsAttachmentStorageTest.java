package com.keystone.fsm;

import com.keystone.fsm.domain.model.Attachment;
import com.keystone.fsm.exception.BusinessRuleException;
import com.keystone.fsm.service.AttachmentService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the GridFS storage mode used in production, where the container
 * filesystem is ephemeral and photos must survive a redeploy.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "app.uploads.storage=gridfs")
class GridFsAttachmentStorageTest {

    @Autowired private AttachmentService attachmentService;
    @Autowired private GridFsTemplate gridFsTemplate;

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};

    @Test
    void photosRoundTripThroughGridFs() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "site-photo.png", "image/png", PNG);

        Attachment attachment = attachmentService.store(file, "wo-gridfs-1", "user-1", "Tech One");

        assertThat(attachment.getStorage()).isEqualTo("gridfs");
        assertThat(attachment.getStoredName()).matches("[0-9a-f]{24}");
        assertThat(gridFsTemplate.findOne(Query.query(
                Criteria.where("_id").is(new ObjectId(attachment.getStoredName()))))).isNotNull();

        try (InputStream in = attachmentService.load(attachment).getInputStream()) {
            assertThat(in.readAllBytes()).isEqualTo(PNG);
        }

        attachmentService.delete(attachment);

        assertThat(gridFsTemplate.findOne(Query.query(
                Criteria.where("_id").is(new ObjectId(attachment.getStoredName()))))).isNull();
        assertThatThrownBy(() -> attachmentService.load(attachment))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Attachment file is missing");
    }

    @Test
    void refusedContentTypesNeverReachGridFs() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "hello".getBytes());

        assertThatThrownBy(() -> attachmentService.store(file, "wo-gridfs-2", "user-1", "Tech One"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only JPEG, PNG, WEBP and GIF images are accepted");
    }
}
