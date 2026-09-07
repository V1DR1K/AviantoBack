package com.avianto.back;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class RevisionWorkflowTest {
  private final DataRepository db = mock(DataRepository.class);
  private final ApiService api = new ApiService(db, mock(PasswordEncoder.class));

  @Test
  void readingRevisionDoesNotCreateOrRestoreIt() {
    Ficha ficha = ficha();
    Revision revision = revision(ficha);
    when(db.get(Ficha.class, ficha.id)).thenReturn(ficha);
    when(db.one(anyString(), eq(Revision.class), anyMap())).thenReturn(revision);

    ApiDtos.RevisionResponse response = api.revision(ficha.id);

    assertEquals(revision.id, response.id());
    verify(db, never()).getForUpdate(Ficha.class, ficha.id);
    verify(db, never()).persist(any());
  }

  @Test
  void preparingRevisionExplicitlyCreatesMissingControls() {
    Ficha ficha = ficha();
    Revision revision = revision(ficha);
    ControlRevision control = new ControlRevision(); control.nombre = "Luces"; control.activo = true;
    when(db.getForUpdate(Ficha.class, ficha.id)).thenReturn(ficha);
    when(db.one(anyString(), eq(Revision.class), anyMap())).thenReturn(revision);
    when(db.all(anyString(), eq(ControlRevision.class), anyMap())).thenReturn(List.of(control));

    ApiDtos.RevisionResponse response = api.prepararRevision(ficha.id);

    assertEquals(1, response.controles().size());
    verify(db).persist(isA(RevisionControl.class));
  }

  private static Ficha ficha() {
    Ficha ficha = new Ficha(); ficha.numero = "F-1"; ficha.estado = FichaState.REVISION; return ficha;
  }

  private static Revision revision(Ficha ficha) {
    Revision revision = new Revision(); revision.ficha = ficha; return revision;
  }
}
