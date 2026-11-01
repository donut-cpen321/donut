import { type NextFunction, type Request, type Response, Router} from 'express';
import { logout, refreshTokens } from '../auth/auth.service';
import { requireAuth } from '../auth/requireAuth';
import { getProfile, login } from './users.service';

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

usersRouter.get(
    '/me',
    (req: Request, res: Response, next: NextFunction) => {
        requireAuth(req, res, next);
    },
    async (req: Request, res: Response) => {
        if (!req.userId) {
            res.sendStatus(401);
            return;
        }

        try {
            const user = await getProfile(req.userId);
            if (user === null) {
                res.status(404).json({ error: 'User not found' });
                return;
            }
            res.status(200).json({ user });
        } catch (e) {
            console.error(e);
            res.status(500).json({ error: 'Internal server error' });
        }
    },
);
