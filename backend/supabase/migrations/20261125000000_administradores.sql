-- Golpe do Presente - administradores promovidos pelo painel
-- Aplicar com:  supabase db push   (ou colar no SQL Editor do painel do Supabase)
--
-- Os administradores FIXOS continuam vindo da variável ADMINS_EMAIL do servidor.
-- Esta tabela guarda quem foi PROMOVIDO por outro administrador no painel.
-- Enquanto ela não existir, a API continua funcionando: só não é possível
-- promover ninguém (apenas os administradores de ADMINS_EMAIL têm acesso).

create table if not exists public.administradores (
    cliente_id   bigint    primary key references public.clientes (id) on delete cascade,
    promovido_em timestamp not null default now(),
    promovido_por bigint   references public.clientes (id) on delete set null
);

-- Mesmo padrão das outras tabelas: RLS ligado e sem policy, então a chave
-- pública do Supabase não lê nada aqui; só o back-end acessa.
alter table public.administradores enable row level security;
