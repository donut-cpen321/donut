import { type Request, type Response, type NextFunction, Router} from 'express';
import { login } from './users.service';

export const usersRouter = Router();

usersRouter.use((req: Request, res: Response, next: NextFunction) => {
    next();
})

usersRouter.post('/login', async (req: Request, res: Response) => {
    await login(req, res);
});
