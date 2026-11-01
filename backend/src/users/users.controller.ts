import { type Request, type Response, Router} from 'express';
import { logout, refreshTokens } from '../auth/auth.service';
import { login } from './users.service';

export const usersRouter = Router();

usersRouter.post('/login', async (req: Request, res: Response) => {
    await login(req, res);
});

usersRouter.post('/refresh', async (req: Request, res: Response) => {
    await refreshTokens(req, res);
});

usersRouter.post('/logout', async (req: Request, res: Response) => {
    await logout(req, res);
});
