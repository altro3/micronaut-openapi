package io.micronaut.openapi.test.api

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.multipart.CompletedFileUpload
import io.micronaut.openapi.test.model.*
import io.micronaut.openapi.test.model.ColorEnum.Companion.fromValue
import reactor.core.publisher.Mono
import java.io.ByteArrayOutputStream

@Controller
open class RequestBodyController : RequestBodyApi {

    @Post(value = "/echo", consumes = ["text/plain"], produces = ["text/plain"])
    fun echo(request: String): String {
        return request
    }

    override fun sendSimpleModel(simpleModel: SimpleModel?): SimpleModel {
        return simpleModel!!
    }

    override fun sendValidatedCollection(requestBody: List<List<String>>?) {
    }

    override fun sendListOfSimpleModels(simpleModels: List<SimpleModel>?): List<SimpleModel> {
        return simpleModels!!
    }

    override fun sendModelWithRequiredProperties(modelWithRequiredProperties: ModelWithRequiredProperties?): ModelWithRequiredProperties {
        return modelWithRequiredProperties!!
    }

    override fun sendDateModel(dateModel: DateModel?): DateModel {
        return dateModel!!
    }

    override fun sendEnum(body: String): ColorEnum {
        return fromValue(body.replace("\"", ""))
    }

    override fun sendEnumList(colorEnums: List<ColorEnum>): List<ColorEnum> {
        return colorEnums
    }

    override fun sendModelWithMapProperty(modelWithMapProperty: ModelWithMapProperty): ModelWithMapProperty {
        return modelWithMapProperty
    }

    override fun sendModelWithValidatedListProperty(modelWithValidatedListProperty: ModelWithValidatedListProperty): Unit {
    }

    override fun sendNestedModel(nestedModel: NestedModel): NestedModel {
        return nestedModel
    }

    override fun sendModelWithInnerEnum(modelWithInnerEnum: ModelWithInnerEnum): ModelWithInnerEnum {
        return modelWithInnerEnum
    }

    override fun sendModelWithDiscriminator(animal: Animal): Animal {
        return animal
    }

    override fun sendBytes(body: ByteArray?): ByteArray {
        return body!!
    }

    override fun sendModelWithEnumList(modelWithEnumList: ModelWithEnumList): ModelWithEnumList {
        return modelWithEnumList
    }

    override fun sendFile(file: CompletedFileUpload?): ByteArray {

            val inputStream = file!!.inputStream
            val outputStream = ByteArrayOutputStream()
            outputStream.write("name: ".toByteArray())
            outputStream.write(file.filename.toByteArray())
            outputStream.write(", content: ".toByteArray())
            inputStream.transferTo(outputStream)
            inputStream.close()
        return     outputStream.toByteArray()
    }
}
