-- Golpe do Presente - cada pessoa pode denunciar o mesmo número no máximo 2 vezes
-- Aplicar com:  supabase db push   (ou colar no SQL Editor do painel do Supabase)
-- Pode rodar mais de uma vez sem problema.
--
-- Antes: UNIQUE (cliente_id, golpista_id) = 1 denúncia por pessoa por número.
-- Agora: cada denúncia ocupa uma "vaga" (ordem 1 ou 2) e o UNIQUE inclui a vaga.
-- Assim o próprio banco impede a 3ª denúncia, mesmo com requisições simultâneas.

alter table public.denuncias add column if not exists ordem integer not null default 1;

alter table public.denuncias drop constraint if exists denuncias_cliente_golpista_unico;

do $$
begin
    if not exists (select 1 from pg_constraint where conname = 'denuncias_cliente_golpista_ordem_unico') then
        alter table public.denuncias
            add constraint denuncias_cliente_golpista_ordem_unico unique (cliente_id, golpista_id, ordem);
    end if;
    if not exists (select 1 from pg_constraint where conname = 'denuncias_ordem_valida') then
        alter table public.denuncias
            add constraint denuncias_ordem_valida check (ordem between 1 and 2);
    end if;
end $$;
