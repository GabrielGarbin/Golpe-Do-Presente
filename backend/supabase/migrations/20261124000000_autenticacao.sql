-- Golpe do Presente - autenticação (login com e-mail e senha + JWT)
-- Aplicar com:  supabase db push   (ou colar no SQL Editor do painel do Supabase)

-- ---------------------------------------------------------------------------
-- Credenciais: hash da senha (PBKDF2 + sal), SEPARADO da tabela clientes para
-- que o hash nunca saia junto com os dados do cliente nas respostas da API.
-- A senha em si nunca é guardada. Apagar o cliente apaga a credencial.
-- ---------------------------------------------------------------------------
create table if not exists public.credenciais (
    cliente_id bigint primary key references public.clientes (id) on delete cascade,
    senha_hash text      not null,
    criado_em  timestamp not null default now()
);

-- Mesmo padrão das outras tabelas: RLS ligado e sem policy, então a chave
-- pública do Supabase não lê nada aqui; só o back-end (dono das tabelas) acessa.
alter table public.credenciais enable row level security;

-- ---------------------------------------------------------------------------
-- E-mail único (sem diferenciar maiúsculas), pois é o identificador de login.
-- ATENÇÃO: se já existirem dois clientes com o mesmo e-mail, esta linha falha;
-- corrija os duplicados antes (select lower(email), count(*) from clientes
-- group by 1 having count(*) > 1).
-- ---------------------------------------------------------------------------
create unique index if not exists clientes_email_unico
    on public.clientes (lower(email))
    where email is not null;
