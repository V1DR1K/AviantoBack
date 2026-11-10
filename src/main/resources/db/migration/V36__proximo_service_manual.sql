-- Fecha del próximo service cargada a mano por moto. Si está vacía, se calcula desde el último service y el periodo en meses.
ALTER TABLE motovehiculo ADD COLUMN proximo_service_fecha DATE;
