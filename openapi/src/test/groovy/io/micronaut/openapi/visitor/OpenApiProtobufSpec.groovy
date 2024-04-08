package io.micronaut.openapi.visitor

import io.micronaut.openapi.AbstractOpenApiTypeElementSpec

class OpenApiProtobufSpec extends AbstractOpenApiTypeElementSpec {

    void "test protobuf parameters"() {

        when:
        buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.openapi.proto.ProductsListProto;

@Controller
class ControllerThree {

    @Post("/myObj")
    MyObj myMethod(@Body ProductsListProto productsListProto) {
        return null;
    }
}

class MyObj {

    public String myProp;
}

@jakarta.inject.Singleton
class MyBean {}
''')
        then:
        Utils.testReference

        when:
        def openApi = Utils.testReference
        def subObject = openApi.components.schemas.SubObjectProto
        def product = openApi.components.schemas.ProductProto
        def productsList = openApi.components.schemas.ProductsListProto

        then:
        subObject
        subObject.properties.size() == 1
        subObject.properties.reqField.type == 'integer'

        product
        product.properties.domain.type == 'integer'
        product.properties.uintField.type == 'integer'
        product.properties.int32.type == 'integer'
        product.properties.int64.type == 'integer'
        product.properties.enumVal.$ref == "#/components/schemas/MyEnum"

        productsList
        productsList.properties.size() == 1
        productsList.properties.products.type == 'array'
        productsList.properties.products.items.$ref == "#/components/schemas/ProductProto"
    }

    void "test protobuf enum"() {

        when:
        buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.openapi.proto.ProductsListProto;

@Controller
class ControllerThree {

    @Post("/myObj")
    MyObj myMethod(@Body ProductsListProto productsListProto) {
        return null;
    }
}

class MyObj {

    public MyEnum myProp;
}

/**
 * Protobuf enum {@code proto.product.MyEnum}
 */
enum MyEnum
    implements com.google.protobuf.ProtocolMessageEnum {
  /**
   * <code>ENUM_VAL1 = 0;</code>
   */
  ENUM_VAL1(0),
  /**
   * <code>ENUM_VAL2 = 1;</code>
   */
  ENUM_VAL2(1),
  UNRECOGNIZED(-1),
  ;

  /**
   * <code>ENUM_VAL1 = 0;</code>
   */
  public static final int ENUM_VAL1_VALUE = 0;
  /**
   * <code>ENUM_VAL2 = 1;</code>
   */
  public static final int ENUM_VAL2_VALUE = 1;


  public final int getNumber() {
    if (this == UNRECOGNIZED) {
      throw new IllegalArgumentException(
          "Can't get the number of an unknown enum value.");
    }
    return value;
  }

  /**
   * @param value The numeric wire value of the corresponding enum entry.
   * @return The enum associated with the given numeric wire value.
   * @deprecated Use {@link #forNumber(int)} instead.
   */
  @Deprecated
  public static MyEnum valueOf(int value) {
    return forNumber(value);
  }

  /**
   * @param value The numeric wire value of the corresponding enum entry.
   * @return The enum associated with the given numeric wire value.
   */
  public static MyEnum forNumber(int value) {
    switch (value) {
      case 0: return ENUM_VAL1;
      case 1: return ENUM_VAL2;
      default: return null;
    }
  }

  public static com.google.protobuf.Internal.EnumLiteMap<MyEnum>
      internalGetValueMap() {
    return internalValueMap;
  }
  private static final com.google.protobuf.Internal.EnumLiteMap<
      MyEnum> internalValueMap =
        new com.google.protobuf.Internal.EnumLiteMap<MyEnum>() {
          public MyEnum findValueByNumber(int number) {
            return MyEnum.forNumber(number);
          }
        };

  public final com.google.protobuf.Descriptors.EnumValueDescriptor
      getValueDescriptor() {
    if (this == UNRECOGNIZED) {
      throw new IllegalStateException(
          "Can't get the descriptor of an unrecognized enum value.");
    }
    return getDescriptor().getValues().get(ordinal());
  }
  public final com.google.protobuf.Descriptors.EnumDescriptor
      getDescriptorForType() {
    return getDescriptor();
  }
  public static final com.google.protobuf.Descriptors.EnumDescriptor
      getDescriptor() {
    return ProductsProtos.getDescriptor().getEnumTypes().get(0);
  }

  private static final MyEnum[] VALUES = values();

  public static MyEnum valueOf(
      com.google.protobuf.Descriptors.EnumValueDescriptor desc) {
    if (desc.getType() != getDescriptor()) {
      throw new IllegalArgumentException(
        "EnumValueDescriptor is not for this type.");
    }
    if (desc.getIndex() == -1) {
      return UNRECOGNIZED;
    }
    return VALUES[desc.getIndex()];
  }

  private final int value;

  private MyEnum(int value) {
    this.value = value;
  }

  // @@protoc_insertion_point(enum_scope:proto.product.MyEnum)
}

@jakarta.inject.Singleton
class MyBean {}
''')
        then:
        Utils.testReference

        when:
        def openApi = Utils.testReference
        def myObj = openApi.components.schemas.MyObj

        then:
        myObj
    }
}
