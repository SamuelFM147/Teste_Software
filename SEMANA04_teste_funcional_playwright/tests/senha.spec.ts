import { test, expect } from '@playwright/test';

/**
 * Teste funcional do cadastro de senha (/senha).
 *
 * Regras declaradas na própria página:
 *  - de 8 a 20 caracteres;
 *  - ao menos uma letra maiúscula, uma minúscula e um número;
 *  - espaços não são permitidos;
 *  - a confirmação precisa ser idêntica à senha.
 *
 * A validação de formato acontece ANTES da comparação com a confirmação,
 * então uma senha fora do padrão nunca produz "As senhas não coincidem".
 *
 * Integrantes: Samuel Fuentes Michels (RA 24011114-2)
 *              Felipe de Almeida Matrone (RA 24051005-2)
 */

const FORA_DO_PADRAO = 'Senha fora do padrão';
const NAO_COINCIDEM = 'As senhas não coincidem';
const CADASTRADA = 'Senha cadastrada';

type Caso = {
  senha: string;
  confirmacao: string;
  classe: string;
};

/** Caminho válido: formato correto e confirmação idêntica. */
const casosValidos: Caso[] = [
  { senha: 'Senha123',             confirmacao: 'Senha123',             classe: 'limite mínimo: 8 caracteres' },
  { senha: 'Senha1234',            confirmacao: 'Senha1234',            classe: 'acima do limite mínimo: 9 caracteres' },
  { senha: 'Abcdefghij123456789',  confirmacao: 'Abcdefghij123456789',  classe: 'abaixo do limite máximo: 19 caracteres' },
  { senha: 'Abcdefghij123456789A', confirmacao: 'Abcdefghij123456789A', classe: 'limite máximo: 20 caracteres' },
  { senha: 'Senha123!@#',          confirmacao: 'Senha123!@#',          classe: 'caracteres especiais são permitidos' },
];

/** Classes inválidas de formato: cada linha viola exatamente uma regra. */
const casosForaDoPadrao: Caso[] = [
  { senha: 'Senha12',               confirmacao: 'Senha12',               classe: '7 caracteres (limite mínimo - 1)' },
  { senha: 'Abcdefghij123456789AB', confirmacao: 'Abcdefghij123456789AB', classe: '21 caracteres (limite máximo + 1)' },
  { senha: 'senha123',              confirmacao: 'senha123',              classe: 'sem letra maiúscula' },
  { senha: 'SENHA123',              confirmacao: 'SENHA123',              classe: 'sem letra minúscula' },
  { senha: 'SenhaSegura',           confirmacao: 'SenhaSegura',           classe: 'sem número' },
  { senha: 'Senha 123',             confirmacao: 'Senha 123',             classe: 'contém espaço' },
  { senha: ' Senha123',             confirmacao: ' Senha123',             classe: 'espaço no início (não há trim)' },
  { senha: '',                      confirmacao: '',                      classe: 'senha vazia' },
  // A regra de formato tem precedência: mesmo divergindo, a mensagem é de formato.
  { senha: '123',                   confirmacao: 'outra',                 classe: 'formato inválido tem precedência sobre a divergência' },
];

/** Formato válido, mas confirmação diferente. */
const casosDivergentes: Caso[] = [
  { senha: 'Senha123', confirmacao: 'Senha124', classe: 'confirmação difere em um caractere' },
  { senha: 'Senha123', confirmacao: 'senha123', classe: 'confirmação difere apenas na caixa' },
  { senha: 'Senha123', confirmacao: '',         classe: 'confirmação vazia' },
];

async function cadastrar(page: import('@playwright/test').Page, senha: string, confirmacao: string) {
  await page.goto('/senha');
  await page.getByLabel('Nova senha').fill(senha);
  await page.getByLabel('Confirmar senha').fill(confirmacao);
  await page.getByRole('button', { name: 'Cadastrar senha' }).click();
  return page.locator('#resultado');
}

test.describe('cadastro de senha — caminho válido', () => {
  for (const caso of casosValidos) {
    test(`senha de ${caso.senha.length} caracteres — ${caso.classe}`, async ({ page }) => {
      const resultado = await cadastrar(page, caso.senha, caso.confirmacao);

      await expect(resultado).toBeVisible();
      await expect(resultado).toHaveText(CADASTRADA);
      await expect(resultado).toHaveAttribute('role', 'status');
      await expect(resultado).toHaveClass('success');
    });
  }

  test('limpa o formulário após o cadastro', async ({ page }) => {
    const resultado = await cadastrar(page, 'Senha123', 'Senha123');
    await expect(resultado).toHaveText(CADASTRADA);

    await expect(page.getByLabel('Nova senha')).toHaveValue('');
    await expect(page.getByLabel('Confirmar senha')).toHaveValue('');
  });
});

test.describe('cadastro de senha — formato inválido', () => {
  for (const caso of casosForaDoPadrao) {
    test(`${caso.classe}`, async ({ page }) => {
      const resultado = await cadastrar(page, caso.senha, caso.confirmacao);

      await expect(resultado).toBeVisible();
      await expect(resultado).toHaveText(FORA_DO_PADRAO);
      await expect(resultado).toHaveAttribute('role', 'alert');
      await expect(resultado).not.toHaveClass('success');
    });
  }
});

test.describe('cadastro de senha — confirmação divergente', () => {
  for (const caso of casosDivergentes) {
    test(`${caso.classe}`, async ({ page }) => {
      const resultado = await cadastrar(page, caso.senha, caso.confirmacao);

      await expect(resultado).toBeVisible();
      await expect(resultado).toHaveText(NAO_COINCIDEM);
      await expect(resultado).toHaveAttribute('role', 'alert');
      // O formulário não é limpo quando o cadastro falha.
      await expect(page.getByLabel('Nova senha')).toHaveValue(caso.senha);
    });
  }
});

test.describe('cadastro de senha — comportamento da interface', () => {
  test('não exibe resultado antes do primeiro envio', async ({ page }) => {
    await page.goto('/senha');

    await expect(page.locator('#resultado')).toBeHidden();
  });

  test('os campos de senha ficam mascarados', async ({ page }) => {
    await page.goto('/senha');

    await expect(page.getByLabel('Nova senha')).toHaveAttribute('type', 'password');
    await expect(page.getByLabel('Confirmar senha')).toHaveAttribute('type', 'password');
  });

  test('corrigir a confirmação transforma o erro em sucesso', async ({ page }) => {
    const resultado = await cadastrar(page, 'Senha123', 'Senha124');
    await expect(resultado).toHaveText(NAO_COINCIDEM);

    await page.getByLabel('Confirmar senha').fill('Senha123');
    await page.getByRole('button', { name: 'Cadastrar senha' }).click();

    await expect(resultado).toHaveText(CADASTRADA);
    await expect(resultado).toHaveAttribute('role', 'status');
  });

  test('link de retorno leva de volta ao login', async ({ page }) => {
    await page.goto('/senha');
    await page.getByRole('link', { name: 'Voltar ao início' }).click();

    await expect(page).toHaveURL(/\/login$/);
  });
});
