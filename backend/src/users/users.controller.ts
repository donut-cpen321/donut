import { type NextFunction, type Request, type Response, Router} from 'express';
import { z } from 'zod';
import { logout, refreshTokens } from '../auth/auth.service';
import { requireAuth } from '../auth/requireAuth';
import { getProfile, login, loginErrors, type LoginError } from './users.service';
import { loginSchema, refreshTokenSchema } from './users.schemas';

export const usersRouter = Router();

const loginErrorStatus: Record<LoginError, number> = {
    [loginErrors.InvalidToken]: 401,
    [loginErrors.InvalidProfile]: 400,
    [loginErrors.UserCreationFailed]: 500,
};

usersRouter.post('/login', async (req: Request, res: Response) => {
    const parsedBody = loginSchema.safeParse(req.body);
    if (!parsedBody.success) {
        res.status(400).json({
            error: 'Invalid request body',
            details: z.flattenError(parsedBody.error),
        });
        return;
    }

    try {
        const result = await login(parsedBody.data.idToken);
        if (!result.success) {
            res.status(loginErrorStatus[result.error]).json({ error: result.error });
            return;
        }

        const message = result.isNewUser ? 'New user created' : 'Login successful';
        res.status(result.isNewUser ? 201 : 200).json({
            message,
            username: result.username,
            accessToken: result.accessToken,
            refreshToken: result.refreshToken,
        });
    } catch (e) {
        console.error(e);
        res.status(500).json({ error: 'Internal server error' });
    }
});

usersRouter.post('/refresh', async (req: Request, res: Response) => {
    const parsedBody = refreshTokenSchema.safeParse(req.body);
    if (!parsedBody.success) {
        res.status(400).json({
            error: 'Invalid request body',
            details: z.flattenError(parsedBody.error),
        });
        return;
    }

    try {
        const result = await refreshTokens(parsedBody.data.refreshToken);
        if (result === null) {
            res.status(401).json({ error: 'Invalid or expired refresh token' });
            return;
        }
        res.status(200).json(result);
    } catch (e) {
        console.error(e);
        res.status(500).json({ error: 'Internal server error' });
    }
});

usersRouter.post('/logout', async (req: Request, res: Response) => {
    const parsedBody = refreshTokenSchema.safeParse(req.body);
    if (!parsedBody.success) {
        res.status(400).json({
            error: 'Invalid request body',
            details: z.flattenError(parsedBody.error),
        });
        return;
    }

    try {
        await logout(parsedBody.data.refreshToken);
        res.sendStatus(204);
    } catch (e) {
        console.error(e);
        res.status(500).json({ error: 'Internal server error' });
    }
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
