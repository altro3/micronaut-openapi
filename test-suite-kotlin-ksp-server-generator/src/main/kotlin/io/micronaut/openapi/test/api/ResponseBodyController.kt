package io.micronaut.openapi.test.api

import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.exceptions.HttpStatusException
import io.micronaut.http.multipart.CompletedFileUpload
import io.micronaut.http.server.netty.multipart.NettyCompletedFileUpload
import io.micronaut.http.server.types.files.FileCustomizableResponseType
import io.micronaut.http.server.types.files.StreamedFile
import io.micronaut.openapi.test.dated.DatedResponse
import io.micronaut.openapi.test.model.DateModel
import io.micronaut.openapi.test.model.ModelWithValidatedListProperty
import io.micronaut.openapi.test.model.SimpleModel
import io.micronaut.openapi.test.model.StateEnum
import io.netty.handler.codec.http.multipart.MemoryFileUpload
import reactor.core.publisher.Mono
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

@Controller
open class ResponseBodyController : ResponseBodyApi {

    override fun getSimpleModel(): SimpleModel {
        return SIMPLE_MODEL
    }

    override fun getDateTime(): ZonedDateTime {
        return DATE_TIME_INSTANCE
    }

    override fun getDateModel(): DateModel {
        return DATE_MODEL_INSTANCE
    }

    override fun getPaginatedSimpleModel(pageable: Pageable): Page<SimpleModel> {
        return Page.of(SIMPLE_MODELS, pageable, SIMPLE_MODELS.size.toLong())
    }

    override fun getDatedSimpleModel(): DatedResponse<SimpleModel> {
        return DatedResponse(SIMPLE_MODEL, LAST_MODIFIED_DATE)
    }

    override fun getSimpleModelWithNonStandardStatus(): HttpResponse<SimpleModel> {
        return HttpResponse.created(SIMPLE_MODEL)
    }

    override fun getDatedSimpleModelWithNonMappedHeader(): HttpResponse<DatedResponse<SimpleModel>> {
        val datedResponse = DatedResponse(SIMPLE_MODEL, LAST_MODIFIED_DATE)
        return HttpResponse.ok(datedResponse)
                .header("custom-header", "custom-value")
    }

    override fun getSimpleModelWithNonMappedHeader(): HttpResponse<SimpleModel> {
        return HttpResponse.ok(SIMPLE_MODEL)
                .header("custom-header", "custom-value-2")
    }

    override fun getErrorResponse(): Unit {
        throw HttpStatusException(HttpStatus.NOT_FOUND, "This is the error")
    }

    override fun getFile(): CompletedFileUpload {
        val stream = ByteArrayInputStream("My file content".toByteArray())
        val fileUpload = MemoryFileUpload("", "", "", "", StandardCharsets.UTF_8, 12)
        fileUpload.setContent(stream)
        return NettyCompletedFileUpload(fileUpload)
    }

    override fun getModelWithValidatedList(): ModelWithValidatedListProperty {
        return ModelWithValidatedListProperty(objectList = listOf(SimpleModel(color = "a")))
    }

    companion object {

        @JvmField
        val SIMPLE_MODEL = SimpleModel("red", 10L, 10.5F, null, false, listOf("1,1", "2,2", "2,4"))

        val SIMPLE_MODELS = listOf(
                SIMPLE_MODEL,
                SimpleModel("red", 3L, 10.5F),
                SimpleModel(color = "blue", state = StateEnum.RUNNING, points = listOf("1,1", "2,2", "3,3")),
        )

        @JvmField
        val DATE_TIME_INSTANCE: ZonedDateTime = OffsetDateTime.parse("2022-12-04T11:35:00.784Z")
                .atZoneSameInstant(ZoneId.of("America/Toronto"))

        val DATE_MODEL_INSTANCE = DateModel(LocalDate.of(2023, 6, 27), DATE_TIME_INSTANCE)

        const val LAST_MODIFIED_STRING = "2023-01-24T10:15:59.100+06:00"
        val LAST_MODIFIED_DATE: ZonedDateTime = ZonedDateTime.parse(LAST_MODIFIED_STRING)
    }
}
