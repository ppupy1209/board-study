package board.media.controller;

import board.media.api.AttachMediaRequest;
import board.media.api.MediaResponse;
import board.media.api.PresignUploadRequest;
import board.media.api.UploadTicketResponse;
import board.media.service.MediaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/v1/media")
@RequiredArgsConstructor
public class MediaController {
    private final MediaService mediaService;

    @PostMapping("/uploads/presign")
    @ResponseStatus(HttpStatus.CREATED)
    public UploadTicketResponse presign(@Valid @RequestBody PresignUploadRequest request) {
        return mediaService.createDirectUpload(request);
    }

    @PostMapping("/uploads/{mediaId}/complete")
    public MediaResponse complete(@PathVariable String mediaId) {
        return mediaService.completeDirectUpload(mediaId);
    }

    @PostMapping(value = "/uploads/proxy", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MediaResponse proxyUpload(@RequestPart("file") MultipartFile file) {
        return mediaService.uploadThroughApplication(file);
    }

    @PostMapping("/articles/{articleId}")
    public List<MediaResponse> attach(
            @PathVariable Long articleId,
            @Valid @RequestBody AttachMediaRequest request
    ) {
        return mediaService.attach(articleId, request.mediaIds());
    }

    @GetMapping("/articles/{articleId}")
    public List<MediaResponse> readByArticle(@PathVariable Long articleId) {
        return mediaService.readByArticle(articleId);
    }

    @GetMapping("/{mediaId}")
    public MediaResponse read(@PathVariable String mediaId) {
        return mediaService.read(mediaId);
    }

    @PostMapping("/{mediaId}/retry")
    public MediaResponse retry(@PathVariable String mediaId) {
        return mediaService.retry(mediaId);
    }

    @DeleteMapping("/{mediaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String mediaId) {
        mediaService.deleteUnattached(mediaId);
    }
}
