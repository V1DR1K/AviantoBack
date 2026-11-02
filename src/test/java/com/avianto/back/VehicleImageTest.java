package com.avianto.back;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class VehicleImageTest {
  private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
  private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};
  private final DataRepository db = mock(DataRepository.class);
  private final ApiService api = new ApiService(db, mock(PasswordEncoder.class));

  @Test
  void storesMiniaturaAndCompletaAsBase64AndSetsTheVersion() {
    UUID motoId = UUID.randomUUID();
    Motovehiculo moto = moto(motoId);
    when(db.get(Motovehiculo.class, motoId)).thenReturn(moto);
    when(db.one(contains("MotovehiculoImagen"), eq(MotovehiculoImagen.class), anyMap())).thenReturn(null);

    ApiDtos.VehicleImageResponse response = api.setVehicleImage(motoId, request("Frente.webp", "image/webp", WEBP, WEBP));

    MotovehiculoImagen stored = persisted(MotovehiculoImagen.class);
    assertEquals(motoId, stored.motovehiculoId);
    assertEquals("image/webp", stored.contentType);
    assertEquals(Base64.getEncoder().encodeToString(WEBP), stored.miniaturaBase64);
    assertEquals(Base64.getEncoder().encodeToString(WEBP), stored.completaBase64);
    assertNotNull(moto.imagenActualizadaAt);
    assertEquals(moto.imagenActualizadaAt, response.updatedAt());
  }

  @Test
  void replacesTheExistingImageInsteadOfPersistingASecondRow() {
    UUID motoId = UUID.randomUUID();
    Motovehiculo moto = moto(motoId);
    MotovehiculoImagen existing = new MotovehiculoImagen();
    existing.motovehiculoId = motoId; existing.contentType = "image/jpeg";
    when(db.get(Motovehiculo.class, motoId)).thenReturn(moto);
    when(db.one(contains("MotovehiculoImagen"), eq(MotovehiculoImagen.class), anyMap())).thenReturn(existing);

    api.setVehicleImage(motoId, request("nueva.jpg", "image/jpeg", JPEG, JPEG));

    verify(db, never()).persist(isA(MotovehiculoImagen.class));
    assertEquals("image/jpeg", existing.contentType);
    assertEquals(Base64.getEncoder().encodeToString(JPEG), existing.completaBase64);
  }

  @Test
  void rejectsContentThatDoesNotMatchTheDeclaredFormat() {
    UUID motoId = UUID.randomUUID();
    when(db.get(Motovehiculo.class, motoId)).thenReturn(moto(motoId));

    BusinessException error = assertThrows(BusinessException.class,
        () -> api.setVehicleImage(motoId, request("falsa.png", "image/png", JPEG, JPEG)));

    assertTrue400(error);
    verify(db, never()).persist(isA(MotovehiculoImagen.class));
  }

  @Test
  void rejectsAMiniaturaOverItsLimit() {
    UUID motoId = UUID.randomUUID();
    when(db.get(Motovehiculo.class, motoId)).thenReturn(moto(motoId));
    byte[] grande = Arrays.copyOf(WEBP, ApiService.MAX_MINIATURA_BYTES + 1);

    BusinessException error = assertThrows(BusinessException.class,
        () -> api.setVehicleImage(motoId, request("grande.webp", "image/webp", grande, WEBP)));

    assertTrue400(error);
  }

  @Test
  void rejectsInvalidBase64() {
    UUID motoId = UUID.randomUUID();
    when(db.get(Motovehiculo.class, motoId)).thenReturn(moto(motoId));
    ApiDtos.VehicleImageRequest request = new ApiDtos.VehicleImageRequest("x.webp", "image/webp", "no es base64!!", "no es base64!!", 10, 10);

    BusinessException error = assertThrows(BusinessException.class, () -> api.setVehicleImage(motoId, request));

    assertTrue400(error);
  }

  @Test
  void deletingTheImageClearsTheVersionMarker() {
    UUID motoId = UUID.randomUUID();
    Motovehiculo moto = moto(motoId);
    moto.imagenActualizadaAt = Instant.now();
    MotovehiculoImagen existing = new MotovehiculoImagen();
    existing.motovehiculoId = motoId;
    when(db.get(Motovehiculo.class, motoId)).thenReturn(moto);
    when(db.one(contains("MotovehiculoImagen"), eq(MotovehiculoImagen.class), anyMap())).thenReturn(existing);

    api.deleteVehicleImage(motoId);

    verify(db).remove(existing);
    assertNull(moto.imagenActualizadaAt);
  }

  @Test
  void profilesCarryTheVersionButNoImageBytes() {
    UUID motoId = UUID.randomUUID();
    Motovehiculo moto = moto(motoId);
    moto.imagenActualizadaAt = Instant.parse("2026-11-02T12:00:00Z");
    when(db.get(Motovehiculo.class, motoId)).thenReturn(moto);

    ApiDtos.MotorcycleResponse response = api.moto(motoId);

    assertEquals(moto.imagenActualizadaAt, response.imagenActualizadaAt());
    assertFalse(Arrays.stream(ApiDtos.MotorcycleResponse.class.getRecordComponents()).anyMatch(component -> component.getType() == byte[].class));
  }

  @Test
  void returnsTheDecodedImageForTheRequestedSize() {
    UUID motoId = UUID.randomUUID();
    MotovehiculoImagen existing = new MotovehiculoImagen();
    existing.motovehiculoId = motoId; existing.contentType = "image/webp"; existing.updatedAt = Instant.now();
    existing.miniaturaBase64 = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});
    existing.completaBase64 = Base64.getEncoder().encodeToString(WEBP);
    when(db.get(Motovehiculo.class, motoId)).thenReturn(moto(motoId));
    when(db.one(contains("MotovehiculoImagen"), eq(MotovehiculoImagen.class), anyMap())).thenReturn(existing);

    ApiDtos.VehicleImageFile miniatura = api.vehicleImage(motoId, "miniatura");
    ApiDtos.VehicleImageFile completa = api.vehicleImage(motoId, "completa");

    assertArrayEquals(new byte[]{1, 2, 3}, miniatura.content());
    assertArrayEquals(WEBP, completa.content());
    assertEquals("image/webp", completa.contentType());
  }

  private static ApiDtos.VehicleImageRequest request(String filename, String type, byte[] miniatura, byte[] completa) {
    return new ApiDtos.VehicleImageRequest(filename, type, Base64.getEncoder().encodeToString(miniatura), Base64.getEncoder().encodeToString(completa), 1280, 960);
  }

  private <T> T persisted(Class<T> type) {
    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(db, atLeastOnce()).persist(captor.capture());
    return captor.getAllValues().stream().filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
  }

  private static void assertTrue400(BusinessException error) {
    assertEquals(400, error.status);
  }

  private static Motovehiculo moto(UUID id) {
    Motovehiculo m = new Motovehiculo();
    m.id = id; m.activo = true; m.patente = "AB123CD"; m.modelo = "Wave";
    m.marca = new MarcaMoto(); m.marca.id = UUID.randomUUID(); m.marca.nombre = "Honda";
    return m;
  }
}
